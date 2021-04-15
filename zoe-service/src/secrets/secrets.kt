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
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import java.io.Closeable
import java.time.Duration

interface SecretsProvider : Closeable {

    companion object {
        val secretsPattern = Regex("""secret(?::(\S+))*:(\S+)""")
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
                logger.info("secret retrieved from cache: $secret")
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

class EnvVarsSecretProvider(private val append: String, private val prepend: String) : SecretsProvider {
    private val secretsPattern = Regex("""secret(?::.+)*:(\S+)""")

    override fun decipher(secret: String): String {
        val matches =
            secretsPattern.matchEntire(secret) ?: userError("secret not parsable by ${this::class.java}")

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
class ExecSecretProvider(private val command: List<String>, private val timeout: Duration) : SecretsProvider {
    object ArgumentsPattern {
        const val SecretName = "{secretName}"
        const val Context = "{context}"
    }

    private val secretsPattern = Regex("""secret(?::([a-zA-Z0-9_\-.]+))?:(\w+)""")

    override fun decipher(secret: String): String {
        val matches = secretsPattern.matchEntire(secret) ?: userError(
            "secret not parsable by ${ExecSecretProvider::class.java} (regex used: $secretsPattern)"
        )

        val (context, secretName) = matches.destructured
        val resolvedCommand = command.map {
            when (it) {
                ArgumentsPattern.SecretName -> secretName
                ArgumentsPattern.Context -> context
                else -> it
            }
        }

        val result = exec(
            command = arrayOf(*resolvedCommand.toTypedArray()),
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
    credentials: AwsCredentialsProvider,
    private val region: Region?
) : SecretsProvider {
    private val secretsPattern = Regex("""secret(?::.+)*:(\S+)""")

    private val client =
        SecretsManagerClient
            .builder()
            .let { builder -> region?.let(builder::region) ?: builder }
            .credentialsProvider(credentials)
            .build()

    override fun decipher(secret: String): String {
        val matches = secretsPattern.matchEntire(secret) ?: userError(
            "secret not parsable by ${AwsSecretsManagerProvider::class.java} (regex used: $secretsPattern)"
        )

        val (secretName) = matches.destructured

        return client
            .getSecretValue { it.secretId(secretName) }
            ?.secretString()
            ?: userError("secret not found: $secretName")
    }

}