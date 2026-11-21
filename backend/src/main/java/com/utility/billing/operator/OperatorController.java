package com.utility.billing.operator;

import com.utility.billing.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/operator")
@PreAuthorize("hasRole('OPERATOR')")
@Validated
public class OperatorController {
    private final OperatorService operators;
    public OperatorController(OperatorService operators){this.operators=operators;}
    private String ip(HttpServletRequest req){return req.getRemoteAddr();}

    @GetMapping("/billing-connections") public ApiResponse<List<Map<String,Object>>> billingConnections(@AuthenticationPrincipal String actor,@RequestParam @NotBlank @Pattern(regexp="\\d{4}-(0[1-9]|1[0-2])")String cycle){return ApiResponse.ok("Billable customer connections",operators.billingConnections(actor,cycle));}
    @GetMapping("/customers") public ApiResponse<OperatorService.PageResult> customers(@AuthenticationPrincipal String actor,@RequestParam(required=false)String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="fullName")String sort,@RequestParam(defaultValue="ASC")String direction){return ApiResponse.ok("Assigned customers",operators.customers(actor,q,page,size,sort,direction));}
    @GetMapping("/customers/{customerId}/connections") public ApiResponse<List<Map<String,Object>>> connections(@AuthenticationPrincipal String actor,@PathVariable long customerId){return ApiResponse.ok("Customer connections",operators.customerConnections(actor,customerId));}
    @GetMapping("/readings/previous") public ApiResponse<Map<String,Object>> previous(@AuthenticationPrincipal String actor,@RequestParam @Positive long connectionId){return ApiResponse.ok("Previous meter reading",operators.previousReading(actor,connectionId));}
    @PostMapping(value="/readings",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ApiResponse<Map<String,Object>> createReading(@AuthenticationPrincipal String actor,@Valid @RequestPart("reading") ReadingRequest r,@RequestPart(value="photo",required=false)MultipartFile photo,HttpServletRequest req){
        return ApiResponse.ok("Meter reading recorded",operators.createReading(new OperatorService.ReadingInput(r.connectionId(),r.currentReading(),r.readingDate(),r.cycle(),r.rollover(),r.meterReset(),r.meterMaximum(),r.remarks()),photo,actor,ip(req)));
    }
    @GetMapping("/readings") public ApiResponse<OperatorService.PageResult> readings(@AuthenticationPrincipal String actor,@RequestParam(required=false)String q,@RequestParam(required=false)String cycle,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="readingDate")String sort,@RequestParam(defaultValue="DESC")String direction){return ApiResponse.ok("Meter readings",operators.readings(actor,q,cycle,page,size,sort,direction));}
    @GetMapping("/readings/{id}/photo") public ResponseEntity<ByteArrayResource> photo(@AuthenticationPrincipal String actor,@PathVariable long id){
        Map<String,Object> result=operators.readingPhoto(id,actor);String filename=result.get("filename").toString();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.get("mediaType").toString())).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+filename+"\"").body(new ByteArrayResource((byte[])result.get("bytes")));
    }

    @PostMapping("/bills") public ApiResponse<Map<String,Object>> generateBill(@AuthenticationPrincipal String actor,@Valid @RequestBody GenerateBill r,HttpServletRequest req){return ApiResponse.ok("Bill generated",operators.generateBill(r.connectionId(),r.cycle(),r.adjustments(),actor,ip(req)));}
    @PostMapping("/bills/bulk") public ApiResponse<Map<String,Object>> bulk(@AuthenticationPrincipal String actor,@Valid @RequestBody BulkBills r,HttpServletRequest req){return ApiResponse.ok("Bulk bill generation completed",operators.bulkGenerate(r.cycle(),actor,ip(req)));}
    @GetMapping("/bills") public ApiResponse<OperatorService.PageResult> bills(@AuthenticationPrincipal String actor,@RequestParam(required=false)String q,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="dueDate")String sort,@RequestParam(defaultValue="DESC")String direction){return ApiResponse.ok("Bills",operators.bills(actor,q,status,page,size,sort,direction));}
    @PutMapping("/bills/{id}") public ApiResponse<Map<String,Object>> regenerate(@AuthenticationPrincipal String actor,@PathVariable long id,@RequestBody BillEdit r,HttpServletRequest req){return ApiResponse.ok("Bill recalculated",operators.regenerateBill(id,r.adjustments(),actor,ip(req)));}

    @GetMapping("/lpg-bookings") public ApiResponse<OperatorService.PageResult> lpgBookings(@AuthenticationPrincipal String actor,@RequestParam(required=false)String q,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok("LPG bookings",operators.lpgBookings(actor,q,status,page,size));}
    @PostMapping("/lpg-bookings") public ApiResponse<Map<String,Object>> createLpg(@AuthenticationPrincipal String actor,@Valid @RequestBody LpgBooking r,HttpServletRequest req){return ApiResponse.ok("LPG cylinder booked and billed",operators.createLpgBooking(new OperatorService.LpgBookingInput(r.connectionId(),r.cylinderCount(),r.subsidy(),r.deliveryCharge()),actor,ip(req)));}
    @PatchMapping("/lpg-bookings/{id}/status") public ApiResponse<Map<String,Object>> lpgStatus(@AuthenticationPrincipal String actor,@PathVariable long id,@Valid @RequestBody BookingStatus r,HttpServletRequest req){return ApiResponse.ok("LPG delivery status updated",operators.updateLpgStatus(id,r.status(),actor,ip(req)));}

    public record ReadingRequest(@Positive long connectionId,@NotNull @DecimalMin("0.0") BigDecimal currentReading,@NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate readingDate,@NotBlank @Pattern(regexp="\\d{4}-(0[1-9]|1[0-2])")String cycle,boolean rollover,boolean meterReset,@DecimalMin("0.0") BigDecimal meterMaximum,@Size(max=1000)String remarks){}
    public record GenerateBill(@Positive long connectionId,@NotBlank @Pattern(regexp="\\d{4}-(0[1-9]|1[0-2])")String cycle,BigDecimal adjustments){}
    public record BulkBills(@NotBlank @Pattern(regexp="\\d{4}-(0[1-9]|1[0-2])")String cycle){}
    public record BillEdit(@DecimalMin("-999999.99") BigDecimal adjustments){}
    public record LpgBooking(@Positive long connectionId,@Min(1) @Max(20)int cylinderCount,@NotNull @DecimalMin("0.0")BigDecimal subsidy,@NotNull @DecimalMin("0.0")BigDecimal deliveryCharge){}
    public record BookingStatus(@NotBlank String status){}
}

