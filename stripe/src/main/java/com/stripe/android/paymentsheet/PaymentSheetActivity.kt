package com.stripe.android.paymentsheet

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.snackbar.Snackbar
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.PaymentIntentResult
import com.stripe.android.R
import com.stripe.android.Stripe
import com.stripe.android.databinding.ActivityCheckoutBinding
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.paymentsheet.model.PaymentSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class PaymentSheetActivity : AppCompatActivity() {
    private val viewBinding by lazy {
        ActivityCheckoutBinding.inflate(layoutInflater)
    }
    private val bottomSheetBehavior by lazy {
        BottomSheetBehavior.from(viewBinding.bottomSheet)
    }
    private val viewModel by viewModels<PaymentSheetViewModel> {
        PaymentSheetViewModel.Factory(application)
    }
    private val stripe: Stripe by lazy {
        val paymentConfiguration = PaymentConfiguration.getInstance(this)
        Stripe(
            applicationContext,
            publishableKey = paymentConfiguration.publishableKey,
            stripeAccountId = paymentConfiguration.stripeAccountId
        )
    }

    private val fragmentContainerId: Int
        @IdRes
        get() = viewBinding.fragmentContainer.id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        // Handle taps outside of bottom sheet
        viewBinding.root.setOnClickListener {
            animateOut()
        }
        viewModel.error.observe(this) {
            // TODO: Communicate error to caller
            Snackbar.make(viewBinding.coordinator, "Received error: ${it.message}", Snackbar.LENGTH_LONG).show()
        }

        setupBottomSheet()
        setupBuyButton()

        // TODO: Add loading state
        supportFragmentManager.commit {
            replace(fragmentContainerId, PaymentSheetPaymentMethodsListFragment())
        }

        viewModel.transition.observe(this) {
            supportFragmentManager.commit {
                when (it) {
                    PaymentSheetViewModel.TransitionTarget.AddCard -> {
                        setCustomAnimations(
                            R.anim.stripe_paymentsheet_transition_enter_from_right,
                            R.anim.stripe_paymentsheet_transition_exit_to_left,
                            R.anim.stripe_paymentsheet_transition_enter_from_left,
                            R.anim.stripe_paymentsheet_transition_exit_to_right
                        )
                        addToBackStack(null)
                        replace(fragmentContainerId, PaymentSheetAddCardFragment())
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        stripe.onPaymentResult(
            requestCode, data,
            object : ApiResultCallback<PaymentIntentResult> {
                override fun onSuccess(result: PaymentIntentResult) {
                    // TODO(smaskell): Communicate result
                    animateOut()
                }

                override fun onError(e: Exception) {
                    viewModel.onError(e)
                }
            }
        )
    }

    private fun setupBuyButton() {
        // TOOD(smaskell): Set text based on currency & amount in payment intent
        viewModel.selection.observe(this) {
            // TODO(smaskell): show Google Pay button when GooglePay selected
            viewBinding.buyButton.isEnabled = it != null
        }
        viewBinding.buyButton.setOnClickListener {
            val args: PaymentSheetActivityStarter.Args? = PaymentSheetActivityStarter.Args.fromIntent(intent)
            if (args == null) {
                viewModel.onError(IllegalStateException("Missing activity args"))
                return@setOnClickListener
            }
            // TODO(smaskell): Show processing indicator
            when (val selection = viewModel.selection.value) {
                PaymentSelection.GooglePay -> TODO("smaskell: handle Google Pay confirmation")
                is PaymentSelection.Saved -> {
                    // TODO(smaskell): Properly set savePaymentMethod/setupFutureUsage
                    stripe.confirmPayment(
                        this,
                        ConfirmPaymentIntentParams.createWithPaymentMethodId(
                            selection.paymentMethodId,
                            args.clientSecret
                        )
                    )
                }
                is PaymentSelection.New -> {
                    stripe.confirmPayment(
                        this,
                        ConfirmPaymentIntentParams.createWithPaymentMethodCreateParams(
                            selection.paymentMethodCreateParams,
                            args.clientSecret
                        )
                    )
                }
                null -> TODO("smaskell: Log error state - this should never happen")
            }
        }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
        bottomSheetBehavior.isHideable = true
        // Start hidden and then animate in after delay
        bottomSheetBehavior.state = STATE_HIDDEN

        lifecycleScope.launch {
            delay(ANIMATE_IN_DELAY)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            bottomSheetBehavior.addBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    }

                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState == STATE_HIDDEN) {
                            finish()
                        }
                    }
                }
            )
        }
    }

    private fun animateOut() {
        // When the bottom sheet finishes animating to its new state,
        // the callback will finish the activity
        bottomSheetBehavior.state = STATE_HIDDEN
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            super.onBackPressed()
        } else {
            animateOut()
        }
    }

    private companion object {
        private const val ANIMATE_IN_DELAY = 300L
    }
}
