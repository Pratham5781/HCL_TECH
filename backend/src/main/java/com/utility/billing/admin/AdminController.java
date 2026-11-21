package com.utility.billing.admin;

import com.utility.billing.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.utility.billing.auth.PortalRole;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {
    private final AdminService admin;
    public AdminController(AdminService admin){this.admin=admin;}
    private String ip(HttpServletRequest request){return request.getRemoteAddr();}

    @GetMapping("/dashboard") public ApiResponse<Map<String,Object>> dashboard(){return ApiResponse.ok("Dashboard metrics",admin.dashboard());}
    @GetMapping("/accounts") public ApiResponse<AdminService.PageResult> accounts(@RequestParam(required=false)String q,@RequestParam(required=false)String role,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="createdAt")String sort,@RequestParam(defaultValue="DESC")String direction){return ApiResponse.ok("Accounts",admin.accounts(q,role,status,page,size,sort,direction));}
    @PostMapping("/accounts") public ApiResponse<Map<String,Object>> create(@Valid @RequestBody AccountCreate r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Account created",admin.createAccount(new AdminService.AccountInput(r.email(),r.password(),r.role(),r.fullName(),r.phone()),actor,ip(req)));}
    @PutMapping("/accounts/{id}") public ApiResponse<Map<String,Object>> update(@PathVariable long id,@Valid @RequestBody AccountUpdate r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Account updated",admin.updateAccount(id,new AdminService.AccountUpdate(r.email(),r.fullName(),r.phone()),actor,ip(req)));}
    @PatchMapping("/accounts/{id}/status") public ApiResponse<Void> status(@PathVariable long id,@Valid @RequestBody StatusChange r,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.setAccountStatus(id,r.status(),actor,ip(req));return ApiResponse.ok("Account status updated",null);}
    @DeleteMapping("/accounts/{id}") public ApiResponse<Void> delete(@PathVariable long id,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.deleteAccount(id,actor,ip(req));return ApiResponse.ok("Account deactivated",null);}
    @PostMapping("/accounts/{id}/password") public ApiResponse<Void> resetPassword(@PathVariable long id,@Valid @RequestBody PasswordReset r,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.resetPassword(id,r.newPassword(),actor,ip(req));return ApiResponse.ok("Password reset and existing refresh tokens revoked",null);}

    @GetMapping("/registrations") public ApiResponse<AdminService.PageResult> registrations(@RequestParam(required=false)String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok("Pending registrations",admin.registrations(q,page,size));}
    @PatchMapping("/registrations/{id}") public ApiResponse<Void> decideRegistration(@PathVariable long id,@Valid @RequestBody Decision r,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.decideRegistration(id,r.decision(),actor,ip(req));return ApiResponse.ok("Registration decision saved",null);}
    @GetMapping("/connection-requests") public ApiResponse<AdminService.PageResult> connectionRequests(@RequestParam(required=false)String q,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok("Connection requests",admin.connectionRequests(q,status,page,size));}
    @PatchMapping("/connection-requests/{id}") public ApiResponse<Void> decideConnection(@PathVariable long id,@Valid @RequestBody Decision r,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.decideConnectionRequest(id,r.decision(),actor,ip(req));return ApiResponse.ok("Connection request decision saved",null);}

    @GetMapping("/data/{resource}") public ApiResponse<AdminService.PageResult> data(@PathVariable String resource,@RequestParam(required=false)String q,@RequestParam(required=false)String status,@RequestParam(required=false)String role,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String sort,@RequestParam(defaultValue="DESC")String direction){return ApiResponse.ok("Records",admin.page(resource,q,status,role,page,size,sort,direction));}
    @GetMapping("/data/{resource}/export") public ResponseEntity<ByteArrayResource> export(@PathVariable String resource,@RequestParam(defaultValue="csv")String format,@RequestParam(required=false)String q,@RequestParam(required=false)String status,@RequestParam(required=false)String role){
        AdminService.ExportFile file=admin.export(resource,q,status,role,format);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString()).body(new ByteArrayResource(file.bytes()));
    }

    @GetMapping("/services") public ApiResponse<List<Map<String,Object>>> services(){return ApiResponse.ok("Utility services",admin.services());}
    @PostMapping("/services") public ApiResponse<Map<String,Object>> createService(@Valid @RequestBody ServiceInput r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Service created",admin.createService(r.code(),r.name(),actor,ip(req)));}
    @PutMapping("/services/{id}") public ApiResponse<Map<String,Object>> updateService(@PathVariable long id,@Valid @RequestBody ServiceUpdate r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Service updated",admin.updateService(id,r.code(),r.name(),r.active(),actor,ip(req)));}

    @GetMapping("/connection-types") public ApiResponse<List<Map<String,Object>>> connectionTypes(){return ApiResponse.ok("Connection types",admin.connectionTypes());}
    @PostMapping("/connection-types") public ApiResponse<Map<String,Object>> createType(@Valid @RequestBody NameInput r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Connection type created",admin.createConnectionType(r.name(),actor,ip(req)));}
    @PutMapping("/connection-types/{id}") public ApiResponse<Map<String,Object>> updateType(@PathVariable long id,@Valid @RequestBody NameInput r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Connection type updated",admin.updateConnectionType(id,r.name(),actor,ip(req)));}
    @DeleteMapping("/connection-types/{id}") public ApiResponse<Void> deleteType(@PathVariable long id,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.deleteConnectionType(id,actor,ip(req));return ApiResponse.ok("Connection type deleted",null);}

    @GetMapping("/zones") public ApiResponse<List<Map<String,Object>>> zones(){return ApiResponse.ok("Zones",admin.zones());}
    @PostMapping("/zones") public ApiResponse<Map<String,Object>> createZone(@Valid @RequestBody ZoneInput r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Zone created",admin.createZone(r.name(),r.description(),actor,ip(req)));}
    @PutMapping("/zones/{id}") public ApiResponse<Map<String,Object>> updateZone(@PathVariable long id,@Valid @RequestBody ZoneInput r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Zone updated",admin.updateZone(id,r.name(),r.description(),actor,ip(req)));}
    @DeleteMapping("/zones/{id}") public ApiResponse<Void> deleteZone(@PathVariable long id,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.deleteZone(id,actor,ip(req));return ApiResponse.ok("Zone deleted and connections unassigned",null);}

    @GetMapping("/tariffs") public ApiResponse<List<Map<String,Object>>> tariffs(){return ApiResponse.ok("Tariff slabs",admin.tariffs());}
    @PostMapping("/tariffs") public ApiResponse<Map<String,Object>> createTariff(@Valid @RequestBody TariffInput r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Tariff created",admin.createTariff(r.toService(),actor,ip(req)));}
    @PutMapping("/tariffs/{id}") public ApiResponse<Map<String,Object>> updateTariff(@PathVariable long id,@Valid @RequestBody TariffInput r,@AuthenticationPrincipal String actor,HttpServletRequest req){return ApiResponse.ok("Tariff updated",admin.updateTariff(id,r.toService(),actor,ip(req)));}
    @DeleteMapping("/tariffs/{id}") public ApiResponse<Void> deleteTariff(@PathVariable long id,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.deleteTariff(id,actor,ip(req));return ApiResponse.ok("Tariff deleted",null);}

    @GetMapping("/assignments") public ApiResponse<Map<String,Object>> assignments(){return ApiResponse.ok("Operator assignments",admin.assignments());}
    @PutMapping("/assignments/operators/{operatorId}/zones") public ApiResponse<Void> assignZones(@PathVariable long operatorId,@Valid @RequestBody IdList r,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.assignZones(operatorId,r.ids(),actor,ip(req));return ApiResponse.ok("Operator zones assigned",null);}
    @PutMapping("/assignments/operators/{operatorId}/customers") public ApiResponse<Void> assignCustomers(@PathVariable long operatorId,@Valid @RequestBody IdList r,@AuthenticationPrincipal String actor,HttpServletRequest req){admin.assignCustomers(operatorId,r.ids(),actor,ip(req));return ApiResponse.ok("Operator customers assigned",null);}

    @GetMapping("/reports") public ApiResponse<Map<String,Object>> reports(){return ApiResponse.ok("Administrative reports",admin.reports());}

    public record AccountCreate(@Email @NotBlank String email,@NotBlank @Size(min=10,max=72) String password,@NotNull PortalRole role,@NotBlank @Size(max=160) String fullName,@Size(max=30) String phone){}
    public record AccountUpdate(@Email @NotBlank String email,@NotBlank @Size(max=160) String fullName,@Size(max=30) String phone){}
    public record StatusChange(@NotBlank String status){}
    public record PasswordReset(@NotBlank @Size(min=10,max=72) String newPassword){}
    public record Decision(@NotBlank String decision){}
    public record ServiceInput(@NotBlank @Size(max=16) String code,@NotBlank @Size(max=80) String name){}
    public record ServiceUpdate(@NotBlank @Size(max=16) String code,@NotBlank @Size(max=80) String name,boolean active){}
    public record NameInput(@NotBlank @Size(max=100) String name){}
    public record ZoneInput(@NotBlank @Size(max=100) String name,@Size(max=500) String description){}
    public record IdList(@NotNull List<@NotNull Long> ids){}
    public record TariffInput(@Positive long serviceId,@Positive long connectionTypeId,@NotNull @DecimalMin("0.0") BigDecimal rangeStart,BigDecimal rangeEnd,@NotNull @DecimalMin("0.0") BigDecimal ratePerUnit,@NotNull @DecimalMin("0.0") BigDecimal fixedCharge,@NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal taxPercent,@NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal lateFeePercent,@Min(1) @Max(90) int dueDays,@NotNull LocalDate effectiveFrom,LocalDate effectiveTo) {
        AdminService.TariffInput toService(){return new AdminService.TariffInput(serviceId,connectionTypeId,rangeStart,rangeEnd,ratePerUnit,fixedCharge,taxPercent,lateFeePercent,dueDays,effectiveFrom,effectiveTo);}
    }
}

