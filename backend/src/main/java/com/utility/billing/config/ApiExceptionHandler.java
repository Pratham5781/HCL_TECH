package com.utility.billing.config;

import com.utility.billing.common.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiResponse<Void>> status(ResponseStatusException ex){
  String reason=ex.getReason()==null?"Request failed":ex.getReason();
  return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.failure(reason,List.of(reason)));
 }
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex){
  var errors=ex.getBindingResult().getFieldErrors().stream().map(this::message).toList();
  return ResponseEntity.badRequest().body(ApiResponse.failure("Validation failed",errors));
 }
 private String message(FieldError error){return error.getField()+": "+error.getDefaultMessage();}
 @ExceptionHandler(EmptyResultDataAccessException.class) ResponseEntity<ApiResponse<Void>> missing(EmptyResultDataAccessException ex){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure("Record not found",List.of("Record not found")));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException ex){return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure("The change conflicts with an existing record or relationship",List.of("The change conflicts with an existing record or relationship")));}
 @ExceptionHandler(Exception.class) ResponseEntity<ApiResponse<Void>> unexpected(Exception ex){
  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure("Unexpected server error",List.of("Unexpected server error")));
 }
}

