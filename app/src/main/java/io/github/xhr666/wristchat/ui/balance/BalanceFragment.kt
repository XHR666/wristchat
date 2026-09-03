package io.github.xhr666.wristchat.ui.balance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import io.github.xhr666.wristchat.databinding.FragmentBalanceBinding
import io.github.xhr666.wristchat.ui.common.RoundInsets

class BalanceFragment : Fragment() {

    private var _binding: FragmentBalanceBinding? = null
    private val binding get() = _binding!!
    private val vm: BalanceViewModel by viewModels { BalanceViewModelFactory(requireActivity().application) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBalanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 圆屏安全区:顶部栏动态收窄
        binding.root.post {
            val inset = RoundInsets.horizontalInsetPx(binding.root, binding.topBar.top.toFloat() + binding.topBar.height / 2f)
            val minInset = (10 * resources.displayMetrics.density).toInt()
            binding.topBar.setPadding(inset.coerceAtLeast(minInset), binding.topBar.paddingTop, inset.coerceAtLeast(minInset), binding.topBar.paddingBottom)
        }
        binding.btnRefresh.setOnClickListener { vm.refresh() }
        binding.timeCapsule.bind(binding.scroll)
        binding.indicatorWrap.bindChild()
        vm.ui.observe(viewLifecycleOwner) { u ->
            if (u == null) return@observe
            binding.tvPeakBadge.text = u.peakLabel
            binding.tvTotal.text = u.total
            binding.tvAvailable.text = u.available
            binding.tvToppedUp.text = u.toppedUp
            binding.tvGranted.text = u.granted
            binding.tvCurrency.text = u.currency
            binding.tvTodayUsage.text = u.todayUsage
            binding.tvTodayTokens.text = u.todayTokens
            binding.tvAppTotal.text = u.appTotalCost
            binding.tvUsageSource.text = u.usageSource
            binding.tvHint.text = u.hint
        }
        vm.updatedAt.observe(viewLifecycleOwner) { binding.tvUpdated.text = "更新于 $it" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
