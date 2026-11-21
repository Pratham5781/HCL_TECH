package com.utility.billing.auth;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LoginRateLimiter {
 private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
 public void check(String key){
  Instant now=Instant.now();
  if(windows.size()>10000)windows.entrySet().removeIf(entry->entry.getValue().until().isBefore(now));
  if(windows.size()>20000){String oldest=windows.keySet().stream().findAny().orElse(null);if(oldest!=null)windows.remove(oldest);}
  Window window=windows.compute(key,(k,old)->old==null||old.until().isBefore(now)?new Window(now.plusSeconds(300),1):new Window(old.until(),old.count()+1));
  if(window.count()>15)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many login attempts. Try again in five minutes.");
 }
 private record Window(Instant until,int count){}
}

