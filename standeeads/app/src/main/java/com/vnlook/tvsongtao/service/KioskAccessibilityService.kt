package com.vnlook.tvsongtao.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper

/**
 * Accessibility Service để tự động đóng thông báo "Hold to unpin"
 * và các popup khác khi Lock Task Mode được kích hoạt
 */
class KioskAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "KioskAccessibility"
        private const val PIN_MESSAGE_KEYWORD = "hold"
        private const val PERMISSION_DIALOG_KEYWORD = "quyền"
        private const val PERMISSION_DIALOG_KEYWORD_EN = "permission"
        
        // Thêm từ khóa để phát hiện popup quyền
        private val PERMISSION_KEYWORDS = listOf(
            "quyền", "permission", "cần quyền", "needs permission",
            "allow", "cho phép", "deny", "từ chối", "hủy", "cancel",
            "settings", "cài đặt"
        )
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val checkDialogsRunnable = object : Runnable {
        override fun run() {
            checkAndDismissDialogs()
            // Kiểm tra mỗi 500ms
            handler.postDelayed(this, 500)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            checkAndDismissDialogs()
        }
    }
    
    /**
     * Kiểm tra và đóng tất cả các dialog
     */
    private fun checkAndDismissDialogs() {
        try {
            val rootNode = rootInActiveWindow ?: return
            
            // Đóng thông báo "Hold to unpin"
            dismissPinMessage(rootNode)
            
            // Đóng popup "Ứng dụng cần quyền để hoạt động"
            dismissPermissionDialog(rootNode)
            
            // Đóng bất kỳ dialog nào khác có thể xuất hiện
            dismissAnyDialog(rootNode)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi kiểm tra và đóng dialog: ${e.message}")
        }
    }

    private fun dismissPinMessage(rootNode: AccessibilityNodeInfo) {
        try {
            // Tìm tất cả các node có text chứa từ khóa "hold"
            val pinMessageNodes = findNodesWithText(rootNode, PIN_MESSAGE_KEYWORD)
            
            if (pinMessageNodes.isNotEmpty()) {
                Log.d(TAG, "Tìm thấy thông báo pin, đang cố gắng đóng")
                
                // Thực hiện thao tác click để đóng thông báo
                for (node in pinMessageNodes) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Đã click để đóng thông báo")
                }
                
                // Hoặc thử nhấn nút Back
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi cố gắng đóng thông báo pin: ${e.message}")
        }
    }
    
    /**
     * Đóng popup "Ứng dụng cần quyền để hoạt động"
     */
    private fun dismissPermissionDialog(rootNode: AccessibilityNodeInfo) {
        try {
            // Tìm các node có text chứa từ khóa "quyền" hoặc "permission"
            val permissionNodesVi = findNodesWithText(rootNode, PERMISSION_DIALOG_KEYWORD)
            val permissionNodesEn = findNodesWithText(rootNode, PERMISSION_DIALOG_KEYWORD_EN)
            
            val permissionNodes = permissionNodesVi + permissionNodesEn
            
            if (permissionNodes.isNotEmpty()) {
                Log.d(TAG, "Tìm thấy popup quyền, đang cố gắng đóng")
                
                // Tìm các nút đóng hoặc hủy
                val buttons = findButtons(rootNode)
                
                // Click vào các nút
                var buttonClicked = false
                for (button in buttons) {
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    buttonClicked = true
                    Log.d(TAG, "Đã click vào nút để đóng popup quyền")
                }
                
                // Nếu không tìm thấy nút nào, thử nhấn Back
                if (!buttonClicked) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Log.d(TAG, "Đã nhấn Back để đóng popup quyền")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi cố gắng đóng popup quyền: ${e.message}")
        }
    }

    private fun findNodesWithText(rootNode: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findNodesWithTextRecursive(rootNode, text.lowercase(), result)
        return result
    }
    
    /**
     * Tìm các nút trong dialog
     */
    private fun findButtons(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        try {
            // Tìm các nút có text là "OK", "Cancel", "Allow", "Deny", "Hủy", "Cho phép", "Từ chối"
            val buttonTexts = listOf("ok", "cancel", "allow", "deny", "hủy", "cho phép", "từ chối", "đóng")
            
            for (buttonText in buttonTexts) {
                val nodes = findNodesWithText(rootNode, buttonText)
                result.addAll(nodes)
            }
            
            // Nếu không tìm thấy nút nào, thử tìm các node có class là Button
            if (result.isEmpty()) {
                findButtonsByClassNameRecursive(rootNode, result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi tìm các nút: ${e.message}")
        }
        
        return result
    }
    
    /**
     * Tìm các nút dựa vào class name
     */
    private fun findButtonsByClassNameRecursive(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        try {
            val className = node.className?.toString() ?: ""
            
            // Kiểm tra nếu node là Button hoặc ImageButton
            if (className.contains("Button", ignoreCase = true)) {
                result.add(node)
                return
            }
            
            // Tìm trong các node con
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findButtonsByClassNameRecursive(child, result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi tìm nút theo class name: ${e.message}")
        }
    }

    private fun findNodesWithTextRecursive(
        node: AccessibilityNodeInfo,
        text: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        try {
            val nodeText = node.text?.toString()?.lowercase() ?: ""
            if (nodeText.contains(text)) {
                result.add(node)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findNodesWithTextRecursive(child, text, result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi tìm node: ${e.message}")
        }
    }

    /**
     * Đóng bất kỳ dialog nào xuất hiện trên màn hình
     */
    private fun dismissAnyDialog(rootNode: AccessibilityNodeInfo) {
        try {
            // Tìm tất cả các nút trên màn hình
            val allButtons = findAllButtons(rootNode)
            
            // Ưu tiên các nút có text là "OK", "Allow", "Cho phép"
            val priorityButtons = allButtons.filter { node ->
                val text = node.text?.toString()?.lowercase() ?: ""
                text.contains("ok") || text.contains("allow") || 
                text.contains("cho phép") || text.contains("đồng ý")
            }
            
            if (priorityButtons.isNotEmpty()) {
                // Click vào nút ưu tiên đầu tiên
                priorityButtons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Đã click vào nút ưu tiên để đóng dialog")
                return
            }
            
            // Nếu không có nút ưu tiên, thử click vào bất kỳ nút nào
            if (allButtons.isNotEmpty()) {
                allButtons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Đã click vào nút để đóng dialog")
                return
            }
            
            // Nếu không tìm thấy nút nào, thử nhấn Back
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi cố gắng đóng dialog: ${e.message}")
        }
    }
    
    /**
     * Tìm tất cả các nút trên màn hình
     */
    private fun findAllButtons(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findAllButtonsRecursive(rootNode, result)
        return result
    }
    
    /**
     * Tìm tất cả các nút trên màn hình (đệ quy)
     */
    private fun findAllButtonsRecursive(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        try {
            val className = node.className?.toString() ?: ""
            val isClickable = node.isClickable
            
            // Nếu node có thể click và là Button hoặc có text
            if (isClickable && (className.contains("Button", ignoreCase = true) || node.text != null)) {
                result.add(node)
            }
            
            // Tìm trong các node con
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findAllButtonsRecursive(child, result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi tìm tất cả các nút: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service bị ngắt")
        // Dừng handler khi service bị ngắt
        handler.removeCallbacks(checkDialogsRunnable)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service đã kết nối")
        
        // Bắt đầu kiểm tra liên tục các dialog
        handler.post(checkDialogsRunnable)
        
        // Cấu hình thêm cho service
        serviceInfo?.apply {
            // Cấp quyền cao hơn cho service
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            // Cập nhật cấu hình
            this@KioskAccessibilityService.serviceInfo = this
        }
    }
}
