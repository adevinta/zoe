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
import com.adevinta.oss.zoe.service.utils.userError
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.schibsted.security.strongbox.sdk.impl.DefaultSimpleSecretsGroup
import com.schibsted.security.strongbox.sdk.types.Region
import com.schibsted.security.strongbox.sdk.types.SecretsGroupIdentifier
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.time.Duration

interface SecretsProvider : Closeable {
    fun isSecret(value: String): Boolean
    fun decipher(secret: String): String

    override fun close() {}
}

class SecretsProviderWithLogging(private val wrapped: SecretsProvider) : SecretsProvider by wrapped {
    override fun decipher(secret: String): String {
        logger.info("deciphering secret : $secret")
        return wrapped.decipher(secret)
    }
}

class SecretsProviderWithCache(
    private val wrapped: SecretsProvider,
    private val store: KeyValueStore,
    private val ttl: Duration
) : SecretsProvider by wrapped {

    private data class CachedSecrets(val secrets: Map<String, CachedValue>)
    private data class CachedValue(val value: String, val timestamp: Long)

    private val fieldName = "secrets"
    private val cached = runBlocking {
        store.getAsJson(fieldName) ?: CachedSecrets(
            mutableMapOf()
        )
    }

    private val secrets = cached.secrets.filter { now() - it.value.timestamp <= ttl.toMillis() }.toMutableMap()

    override fun decipher(secret: String): String =
        secrets.computeIfAbsent(secret) {
            CachedValue(
                wrapped.decipher(secret),
                now()
            )
        }.value

    override fun close() {
        if (secrets != cached.secrets) {
            logger.debug("caching secrets...")
            runBlocking {
                store.putAsJson(
                    fieldName,
                    CachedSecrets(secrets)
                )
            }
        }

        super.close()
    }
}

object NoopSecretsProvider : SecretsProvider {
    override fun isSecret(value: String): Boolean = false
    override fun decipher(secret: String): String = secret
}

class StrongboxProvider(
    private val credentials: AWSCredentialsProvider,
    private val region: String,
    private val group: String
) :
    SecretsProvider {
    private val secretsPattern = Regex("""secret:(?:(\S+):)?:(\S+)""")

    override fun isSecret(value: String): Boolean = secretsPattern.matchEntire(value) != null
    override fun decipher(secret: String): String {
        val matches =
            secretsPattern.matchEntire(secret) ?: userError("secret not parsable by ${StrongboxProvider::class.java}")

        val (profile, secretName) = matches.destructured

        val credentials = if (profile.isNotEmpty()) ProfileCredentialsProvider(profile) else credentials

        logger.info("fetching secret : $secretName (group: $group, profile : $profile)")

        return DefaultSimpleSecretsGroup(SecretsGroupIdentifier(Region.fromName(region), group), credentials)
            .getStringSecret(secretName)
            .orElseThrow { IllegalArgumentException("'$secretName' not found") }
    }

}

class EnvVarsSecretProvider(private val append: String, private val prepend: String) : SecretsProvider {
    private val secretsPattern = Regex("""secret(?::.+)*:(\S+)""")

    override fun isSecret(value: String): Boolean = secretsPattern.matchEntire(value) != null
    override fun decipher(secret: String): String {
        val matches =
            secretsPattern.matchEntire(secret) ?: userError("secret not parsable by ${StrongboxProvider::class.java}")

        val (secretName) = matches.destructured
        val completedSecretName = "$prepend$secretName$append"
        return System.getenv(completedSecretName) ?: userError("secret not found in env : $completedSecretName")
    }

}