package com.harvey.digitalgarden.ledger.chat;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.ledger.chat.ChatDtos.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LedgerChatAttachmentService {
    private final ChatStore store;
    private final Path root;
    public LedgerChatAttachmentService(ChatStore store,@Value("${app.ledger-chat.private-dir:./ledger-private}")String dir,@Value("${app.upload.dir:./uploads}")String publicDir) {
        this.store=store;this.root=Path.of(dir).toAbsolutePath().normalize();
        if(root.startsWith(Path.of(publicDir).toAbsolutePath().normalize()))throw new IllegalArgumentException("账本图片目录必须独立于公开上传目录");
    }
    public Attachment upload(long uid,MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>10*1024*1024)throw BusinessException.badRequest("每张图片最多10MB");
        Path path=null;
        try {
            StoredImage image=ChatImageNormalizer.normalize(file.getBytes());
            Files.createDirectories(root);String name=UUID.randomUUID()+".bin";path=root.resolve(name);Files.write(path,image.bytes(),StandardOpenOption.CREATE_NEW);
            return store.tx(()->{
                store.lock(uid);
                long id=store.insert("INSERT INTO ledger_chat_attachment(user_id,hash,storage_key,content_type,width,height,in_dtm) VALUES(?,?,?,?,?,?,?)",uid,image.hash(),name,image.contentType(),image.width(),image.height(),Instant.now().getEpochSecond());
                return new Attachment(id,image.contentType(),image.width(),image.height());
            });
        } catch(Exception ex) {
            if(path!=null)try{Files.deleteIfExists(path);}catch(Exception ignored){}
            if(ex instanceof BusinessException b)throw b;
            throw BusinessException.badRequest("图片保存失败，请重试");
        }
    }
    public StoredImage loadOwned(long uid,long id) {
        var rows=store.db.queryForList("SELECT * FROM ledger_chat_attachment WHERE user_id=? AND id=?",uid,id);
        if(rows.isEmpty())throw BusinessException.notFound("图片不存在");
        var row=rows.getFirst();Path path=root.resolve((String)row.get("storage_key")).normalize();
        if(!path.startsWith(root))throw BusinessException.notFound("图片不存在");
        try {return new StoredImage(Files.readAllBytes(path),(String)row.get("content_type"),((Number)row.get("width")).intValue(),((Number)row.get("height")).intValue(),(String)row.get("hash"));}
        catch(Exception ex){throw BusinessException.notFound("图片无法读取，请重新上传");}
    }
}
