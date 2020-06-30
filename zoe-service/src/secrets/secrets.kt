// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.secrets

import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.now
import com.adevinta.oss.zoe.service.storage.KeyValueStore
import com.adevinta.oss.zoe.service.storage.getAsJson
import com.adevinta.oss.zoe.service.storage.putAsJson
import com.adevinta.oss.zoe.service.utils.exec
import com.adevinta.oss.zoe.service.utils.userError
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest
import com.schibsted.security.strongbox.sdk.impl.DefaultSimpleSecretsGroup
import com.schibsted.security.strongbox.sdk.types.Region
import com.schibsted.security.strongbox.sdk.types.SecretsGroupIdentifier
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.time.Duration

interface SecretsProvider : Closeable {

    companion object {
        val secretsPattern = Regex("""secret(?::(\S+))*:(\w+)""")
    }

    fun isSecret(value: String): Boolean = secretsPattern.matchEntire(value) != null
    fun decipher(secret: String): String

    override fun close() {}
}

class SecretsProviderWithLogging(private val wrapped: SecretsProvider) : SecretsProvider by wrapped {
    override fun decipher(secret: String): String {
        logger.info("deciphering secret : $secret")
        val result = wrapped.decipher(secret)
        logger.debug("secret deciphered to: $result")
        return result
    }
}

class SecretsProviderWithCache(
    private val wrapped: SecretsProvider,
    private val store: KeyValueStore,
    private val ttl: Duration
) : SecretsProvider by wrapped {

    private data class CachedSecrets(val secrets: Map<String, CachedValue>)
    private data class CachedValue(val value: String, val timestamp: Long)

    private val fieldName = "secrets:${wrapped::class.java.canonicalName}"
    private val secrets = runBlocking {
        val cached = store.getAsJson(fieldName) ?: CachedSecrets(mutableMapOf())
        cached.secrets.filter { now() - it.value.timestamp <= ttl.toMillis() }.toMutableMap()
    }

    override fun decipher(secret: String): String = runBlocking {
        val retrieved = when (val cached = secrets[secret]) {
            null -> {
                val deciphered = CachedValue(wrapped.decipher(secret), now())
                secrets[secret] = deciphered
                deciphered
            }
            else -> {
                logger.debug("secret retrieved from cache: $secret")
                cached
            }
        }

        retrieved.value.also { store.putAsJson(fieldName, CachedSecrets(secrets)) }
    }
}

object NoopSecretsProvider : SecretsProvider {
    override fun isSecret(value: String): Boolean = false
    override fun decipher(secret: String): String = secret
}

class StrongboxProvider(
    private val credentials: AWSCredentialsProvider,
    private val region: String,
    private val defaultGroup: String
) : SecretsProvider {
    private val secretsPattern = Regex("""secret(?::([a-zA-Z0-9_\-.]+))?(?::([a-zA-Z0-9_\-.]+))?:(\w+)""")

    override fun decipher(secret: String): String {
        val matches =
            secretsPattern.matchEntire(secret) ?: userError("secret not parsable by ${StrongboxProvider::class.java}")

        val (profile, parsedGroup, secretName) = matches.destructured

        val credentials = if (profile.isNotEmpty()) ProfileCredentialsProvider(profile) else credentials
        val group = if (parsedGroup.isNotEmpty()) parsedGroup else defaultGroup

        logger.info("fetching secret : $secretName (group: $group, profile : $profile)")

        return DefaultSimpleSecretsGroup(SecretsGroupIdentifier(Region.fromName(region), group), credentials)
            .getStringSecret(secretName)
            .orElseThrow { IllegalArgumentException("'$secretName' not found") }
    }
}

class EnvVarsSecretProvider(private val append: String, private val prepend: String) : SecretsProvider {
    private val secretsPattern = Regex("""secret(?::.+)*:(\S+)""")

    override fun decipher(secret: String): String {
        val matches =
            secretsPattern.matchEntire(secret) ?: userError("secret not parsable by ${StrongboxProvider::class.java}")

        val (secretName) = matches.destructured
        val completedSecretName = "$prepend$secretName$append"
        return System.getenv(completedSecretName) ?: userError("secret not found in env : $completedSecretName")
    }
}

/**
 * A [SecretsProvider] implementation that delegates secrets deciphering to a command.
 *
 * The supplied script should expect the following arguments:
 * - the secret name
 * - the context (optional)
 */
class ExecSecretProvider(private val command: String, private val timeout: Duration) : SecretsProvider {
    private val secretsPattern = Regex("""secret(?::([a-zA-Z0-9_\-.]+))?:(\w+)""")

    override fun decipher(secret: String): String {
        val matches = secretsPattern.matchEntire(secret) ?: userError(
            "secret not parsable by ${ExecSecretProvider::class.java} (regex used: $secretsPattern)"
        )

        val (context, secretName) = matches.destructured

        val result = exec(
            command = arrayOf(command, secretName, context),
            failOnError = true,
            timeout = timeout
        )

        return result.stdout.trim().takeIf { it.isNotEmpty() } ?: userError("command returned an empty secret")
    }
}

/**
 * A [SecretsProvider] implementation that retrieves secrets from AWS secrets manager
 */
class AwsSecretsManagerProvider(
    credentials: AWSCredentialsProvider,
    private val region: String?
) : SecretsProvider {
    private val secretsPattern = Regex("""secret(?::.+)*:(\S+)""")

    private val client =
        AWSSecretsManagerClientBuilder
            .standard()
            .let { region?.let(it::withRegion) ?: it }
            .withCredentials(credentials)
            .build()

    override fun decipher(secret: String): String {
        val matches = secretsPattern.matchEntire(secret) ?: userError(
            "secret not parsable by ${AwsSecretsManagerProvider::class.java} (regex used: $secretsPattern)"
        )

        val (secretName) = matches.destructured

        return client
            .getSecretValue(GetSecretValueRequest().withSecretId(secretName))
            ?.secretString
            ?: userError("secret not found: $secretName")
    }

}