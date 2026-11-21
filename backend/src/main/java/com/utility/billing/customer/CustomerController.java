package com.utility.billing.customer;

import com.utility.billing.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
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

@RestController
@RequestMapping("/api/customer")
@PreAuthorize("hasRole('CUSTOMER')")
@Validated
public class CustomerController {
    private final CustomerService customers;
    public CustomerController(CustomerService customers){this.customers=customers;}

    @GetMapping("/dashboard") public ApiResponse<Map<String,Object>> dashboard(@AuthenticationPrincipal String email){return ApiResponse.ok("Customer dashboard",customers.dashboard(email));}
    @GetMapping("/bills") public ApiResponse<CustomerService.PageResult> bills(@AuthenticationPrincipal String email,@RequestParam(required=false)String q,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok("Bills",customers.bills(email,q,status,page,size));}
    @GetMapping("/bills/{id}") public ApiResponse<Map<String,Object>> bill(@AuthenticationPrincipal String email,@PathVariable long id){return ApiResponse.ok("Bill details",customers.billDetail(email,id));}
    @GetMapping("/bills/{id}/pdf") public ResponseEntity<ByteArrayResource> billPdf(@AuthenticationPrincipal String email,@PathVariable long id){return pdf(customers.billPdf(email,id),"bill-"+id+".pdf");}
    @GetMapping("/readings") public ApiResponse<CustomerService.PageResult> readings(@AuthenticationPrincipal String email,@RequestParam(required=false)Long connectionId,@RequestParam(required=false)String cycle,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok("Consumption history",customers.readings(email,connectionId,cycle,page,size));}
    @GetMapping("/payments") public ApiResponse<CustomerService.PageResult> payments(@AuthenticationPrincipal String email,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok("Payments",customers.payments(email,page,size));}
    @PostMapping("/bills/{id}/payments") public ApiResponse<Map<String,Object>> pay(@AuthenticationPrincipal String email,@PathVariable long id,@Valid @RequestBody PaymentRequest request){return ApiResponse.ok("Mock payment result",customers.pay(email,id,request.amount(),request.method(),request.simulateSuccess()));}
    @GetMapping("/payments/{id}/receipt.pdf") public ResponseEntity<ByteArrayResource> receipt(@AuthenticationPrincipal String email,@PathVariable long id){return pdf(customers.receiptPdf(email,id),"receipt-"+id+".pdf");}
    @GetMapping("/services") public ApiResponse<List<Map<String,Object>>> services(){return ApiResponse.ok("Active services",customers.services());}
    @GetMapping("/connection-requests") public ApiResponse<CustomerService.PageResult> requests(@AuthenticationPrincipal String email,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok("Connection requests",customers.requests(email,page,size));}
    @PostMapping(value="/connection-requests",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ApiResponse<Map<String,Object>> createRequest(@AuthenticationPrincipal String email,@Valid @RequestPart("request")Request request,@RequestPart(value="document",required=false)MultipartFile document){return ApiResponse.ok("Connection request submitted",customers.createRequest(email,new CustomerService.RequestInput(request.serviceId(),request.requestType(),request.desiredAddress(),request.notes()),document));}
    @GetMapping("/connection-requests/{id}/document") public ResponseEntity<ByteArrayResource> document(@AuthenticationPrincipal String email,@PathVariable long id){CustomerService.StoredFile file=customers.requestDocument(email,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType())).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+file.name()+"\"").body(new ByteArrayResource(file.bytes()));}
    @PutMapping("/profile") public ApiResponse<Map<String,Object>> profile(@AuthenticationPrincipal String email,@Valid @RequestBody Profile request){return ApiResponse.ok("Profile updated",customers.updateProfile(email,request.phone()));}

    private ResponseEntity<ByteArrayResource> pdf(byte[] bytes,String filename){return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"").body(new ByteArrayResource(bytes));}
    public record PaymentRequest(@DecimalMin(value="0.01",message="Payment amount must be greater than zero") BigDecimal amount,@NotBlank String method,boolean simulateSuccess){}
    public record Request(@Positive long serviceId,@NotBlank String requestType,@Size(max=500)String desiredAddress,@Size(max=1000)String notes){}
    public record Profile(@NotBlank @Size(max=30)String phone){}
}
