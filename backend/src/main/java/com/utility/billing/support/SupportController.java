package com.utility.billing.support;

import com.utility.billing.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/support") @PreAuthorize("hasRole('HELP_SUPPORT')") @Validated
public class SupportController {
    private final SupportService support;
    public SupportController(SupportService support){this.support=support;}
    private String ip(HttpServletRequest request){return request.getRemoteAddr();}

    @GetMapping("/dashboard") public ApiResponse<Map<String,Object>> dashboard(@AuthenticationPrincipal String actor){return ApiResponse.ok("Support dashboard",support.summary(actor));}
    @GetMapping("/tickets") public ApiResponse<SupportService.PageResult> tickets(@AuthenticationPrincipal String actor,@RequestParam(required=false)String q,@RequestParam(required=false)String service,@RequestParam(required=false)String status,@RequestParam(required=false)String priority,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate dateFrom,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate dateTo,@RequestParam(defaultValue="false")boolean mine,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){if(dateFrom!=null&&dateTo!=null&&dateFrom.isAfter(dateTo))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Start date must be before end date");return ApiResponse.ok("Support tickets",support.tickets(q,service,status,priority,dateFrom==null?null:dateFrom.toString(),dateTo==null?null:dateTo.toString(),mine,page,size,actor));}
    @GetMapping("/tickets/{id}") public ApiResponse<Map<String,Object>> ticket(@AuthenticationPrincipal String actor,@PathVariable long id){return ApiResponse.ok("Ticket details",support.ticket(id,actor));}
    @PostMapping("/tickets/{id}/assign-to-self") public ApiResponse<Map<String,Object>> assign(@AuthenticationPrincipal String actor,@PathVariable long id,HttpServletRequest request){return ApiResponse.ok("Ticket assigned",support.assignToSelf(id,actor,ip(request)));}
    @PatchMapping("/tickets/{id}/status") public ApiResponse<Map<String,Object>> status(@AuthenticationPrincipal String actor,@PathVariable long id,@Valid @RequestBody StatusChange body,HttpServletRequest request){return ApiResponse.ok("Ticket status updated",support.updateStatus(id,body.status(),actor,ip(request)));}
    @PostMapping("/tickets/{id}/comments") public ApiResponse<Map<String,Object>> comment(@AuthenticationPrincipal String actor,@PathVariable long id,@Valid @RequestBody Comment body,HttpServletRequest request){return ApiResponse.ok("Comment added",support.comment(id,body.body(),body.internal(),actor,ip(request)));}
    @PostMapping("/tickets/{id}/escalate") public ApiResponse<Map<String,Object>> escalate(@AuthenticationPrincipal String actor,@PathVariable long id,HttpServletRequest request){return ApiResponse.ok("Ticket escalated",support.escalate(id,actor,ip(request)));}
    @GetMapping("/customers/{id}") public ApiResponse<Map<String,Object>> context(@AuthenticationPrincipal String actor,@PathVariable long id){return ApiResponse.ok("Read-only customer context",support.customerContext(id));}

    @GetMapping("/customer/tickets") @PreAuthorize("hasRole('CUSTOMER')") public ApiResponse<SupportService.PageResult> customerTickets(@AuthenticationPrincipal String email,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok("Your support tickets",support.customerTickets(email,page,size));}
    @PostMapping("/customer/tickets") @PreAuthorize("hasRole('CUSTOMER')") public ApiResponse<Map<String,Object>> createCustomerTicket(@AuthenticationPrincipal String email,@Valid @RequestBody NewTicket body,HttpServletRequest request){return ApiResponse.ok("Support ticket opened",support.createCustomerTicket(email,body.serviceId(),body.category(),body.subject(),body.description(),ip(request)));}
    @GetMapping("/customer/tickets/{id}") @PreAuthorize("hasRole('CUSTOMER')") public ApiResponse<Map<String,Object>> customerTicket(@AuthenticationPrincipal String email,@PathVariable long id){return ApiResponse.ok("Your ticket",support.customerTicket(id,email));}
    @PostMapping("/customer/tickets/{id}/comments") @PreAuthorize("hasRole('CUSTOMER')") public ApiResponse<Map<String,Object>> customerComment(@AuthenticationPrincipal String email,@PathVariable long id,@Valid @RequestBody CustomerComment body,HttpServletRequest request){return ApiResponse.ok("Comment added",support.customerComment(id,email,body.body(),ip(request)));}
    @PostMapping("/customer/tickets/{id}/rating") @PreAuthorize("hasRole('CUSTOMER')") public ApiResponse<Map<String,Object>> rating(@AuthenticationPrincipal String email,@PathVariable long id,@Valid @RequestBody Rating body,HttpServletRequest request){return ApiResponse.ok("Ticket rating saved",support.rateCustomerTicket(id,email,body.rating(),ip(request)));}

    public record StatusChange(@NotBlank String status){}
    public record Comment(@NotBlank @Size(max=4000)String body,boolean internal){}
    public record NewTicket(@Positive long serviceId,@NotBlank String category,@NotBlank @Size(max=180)String subject,@NotBlank @Size(max=5000)String description){}
    public record CustomerComment(@NotBlank @Size(max=4000)String body){}
    public record Rating(@Min(1) @Max(5)int rating){}
}
