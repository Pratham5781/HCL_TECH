package com.utility.billing.operator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhotoStorageService {
    private final Path root;
    private static final Map<String,String> TYPES=Map.of("image/jpeg",".jpg","image/png",".png","image/webp",".webp");
    public PhotoStorageService(@Value("${app.upload-dir:./uploads}") String directory){root=Path.of(directory).toAbsolutePath().normalize();}
    public String store(MultipartFile photo){
        if(photo==null||photo.isEmpty())return null;
        if(photo.getSize()>5*1024*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Meter photo must not exceed 5 MB");
        String extension=TYPES.get(photo.getContentType()==null?"":photo.getContentType().toLowerCase(Locale.ROOT));
        if(extension==null)throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Meter photo must be JPEG, PNG or WebP");
        String name=UUID.randomUUID()+extension;
        try{Files.createDirectories(root);Path destination=root.resolve(name).normalize();if(!destination.startsWith(root))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid photo path");photo.transferTo(destination);return name;}
        catch(IOException ex){throw new IllegalStateException("Could not store meter photo",ex);}
    }
    public byte[] read(String filename){
        if(filename==null||!filename.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp)"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Meter photo not found");
        Path file=root.resolve(filename).normalize();if(!file.startsWith(root)||!Files.isRegularFile(file))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Meter photo not found");
        try{return Files.readAllBytes(file);}catch(IOException ex){throw new IllegalStateException("Could not read meter photo",ex);}
    }
    public String mediaType(String filename){return filename.endsWith(".png")?"image/png":filename.endsWith(".webp")?"image/webp":"image/jpeg";}
}

