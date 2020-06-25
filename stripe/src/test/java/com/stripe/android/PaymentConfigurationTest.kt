package com.stripe.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stripe.android.utils.ParcelUtils
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaymentConfigurationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @BeforeTest
    fun setup() {
        // Make sure we initialize before each test.
        PaymentConfiguration.clearInstance()
    }

    @Test
    fun getInstance_beforeInit_throwsRuntimeException() {
        assertFailsWith<IllegalStateException> {
            PaymentConfiguration.getInstance(context)
        }
    }

    @Test
    fun `stripeAccountId should be persisted`() {
        PaymentConfiguration.init(
            context,
            ApiKeyFixtures.FAKE_PUBLISHABLE_KEY,
            "acct_12345"
        )

        assertThat(
            PaymentConfiguration.getInstance(context)
        ).isEqualTo(
            PaymentConfiguration(
                ApiKeyFixtures.FAKE_PUBLISHABLE_KEY,
                "acct_12345"
            )
        )
    }

    @Test
    fun getInstance_whenInstanceIsNull_loadsFromPrefs() {
        PaymentConfiguration.init(
            context,
            ApiKeyFixtures.FAKE_PUBLISHABLE_KEY,
            betas = setOf(StripeApiBeta.AlipayV1)
        )

        PaymentConfiguration.clearInstance()

        assertThat(
            PaymentConfiguration.getInstance(context)
        ).isEqualTo(
            PaymentConfiguration(
                ApiKeyFixtures.FAKE_PUBLISHABLE_KEY,
                betas = setOf(StripeApiBeta.AlipayV1)
            )
        )
    }

    @Test
    fun testParcel() {
        PaymentConfiguration.init(context, ApiKeyFixtures.FAKE_PUBLISHABLE_KEY)

        val paymentConfig = PaymentConfiguration.getInstance(context)
        assertThat(ParcelUtils.create(paymentConfig))
            .isEqualTo(paymentConfig)
    }
}
