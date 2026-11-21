package com.utility.billing.notification;

import com.utility.billing.common.ApiResponse;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/notifications") @Validated
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications){this.notifications=notifications;}
    @GetMapping public ApiResponse<NotificationService.PageResult> list(@AuthenticationPrincipal String email,@RequestParam(defaultValue="false")boolean unreadOnly,@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok("Notifications",notifications.list(email,unreadOnly,page,size));}
    @PatchMapping("/{id}/read") public ApiResponse<Void> read(@AuthenticationPrincipal String email,@PathVariable long id){notifications.markRead(email,id);return ApiResponse.ok("Notification marked as read",null);}
    @PostMapping("/read-all") public ApiResponse<Integer> readAll(@AuthenticationPrincipal String email){return ApiResponse.ok("Notifications marked as read",notifications.markAllRead(email));}
}
