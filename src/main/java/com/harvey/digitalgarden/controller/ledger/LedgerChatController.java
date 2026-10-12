package com.harvey.digitalgarden.controller.ledger;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.ledger.chat.*;
import com.harvey.digitalgarden.ledger.chat.ChatDtos.*;
import com.harvey.digitalgarden.security.LedgerPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ledger/chat")
public class LedgerChatController {
    private final LedgerChatService chat;
    private final ChatDraftService drafts;
    private final ChatConfirmService confirmation;
    private final LedgerChatAttachmentService attachments;
    private final com.harvey.digitalgarden.ledger.chat.LedgerGuestService guests;
    public LedgerChatController(LedgerChatService chat,ChatDraftService drafts,ChatConfirmService confirmation,LedgerChatAttachmentService attachments,com.harvey.digitalgarden.ledger.chat.LedgerGuestService guests) {
        this.chat=chat;this.drafts=drafts;this.confirmation=confirmation;this.attachments=attachments;
        this.guests=guests;
    }
    @GetMapping("/session") public Result<?> session(@AuthenticationPrincipal LedgerPrincipal p) {return Result.success(guests.view(p.uid()));}
    @GetMapping("/history") public Result<History> history(@AuthenticationPrincipal LedgerPrincipal p,@RequestParam(required=false)Long beforeId,@RequestParam(defaultValue="30")int limit) {return Result.success(chat.history(p.uid(),beforeId,limit));}
    @PostMapping("/messages") public Result<Turn> send(@AuthenticationPrincipal LedgerPrincipal p,@RequestBody Send request) {return Result.success(chat.send(p.uid(),request));}
    @GetMapping("/messages/by-request/{id}") public Result<RequestState> requestState(@AuthenticationPrincipal LedgerPrincipal p,@PathVariable String id) {return Result.success(chat.requestState(p.uid(),id));}
    @PutMapping("/drafts/{did}/items/{iid}") public Result<Draft> edit(@AuthenticationPrincipal LedgerPrincipal p,@PathVariable long did,@PathVariable long iid,@RequestBody Edit request) {return Result.success(drafts.edit(p.uid(),did,iid,request));}
    @DeleteMapping("/drafts/{did}/items/{iid}") public Result<Draft> remove(@AuthenticationPrincipal LedgerPrincipal p,@PathVariable long did,@PathVariable long iid,@RequestParam long expectedVersion) {return Result.success(drafts.remove(p.uid(),did,iid,expectedVersion));}
    @PostMapping("/drafts/{did}/cancel") public Result<Draft> cancel(@AuthenticationPrincipal LedgerPrincipal p,@PathVariable long did,@RequestBody Version request) {return Result.success(drafts.cancel(p.uid(),did,request.expectedVersion()));}
    @PostMapping("/drafts/{did}/confirm") public Result<Draft> confirm(@AuthenticationPrincipal LedgerPrincipal p,@PathVariable long did,@RequestBody Version request) {return Result.success(confirmation.confirm(p.uid(),did,request.expectedVersion()));}
    @PostMapping("/drafts/{did}/revoke") public Result<Draft> revoke(@AuthenticationPrincipal LedgerPrincipal p,@PathVariable long did,@RequestBody Version request) {return Result.success(confirmation.revoke(p.uid(),did,request.expectedVersion()));}
    @PostMapping(value="/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Result<Attachment> upload(@AuthenticationPrincipal LedgerPrincipal p,@RequestParam MultipartFile file) {return Result.success(attachments.upload(p.uid(),file));}
    @GetMapping("/attachments/{id}/content") public ResponseEntity<byte[]> image(@AuthenticationPrincipal LedgerPrincipal p,@PathVariable long id) {
        var image=attachments.loadOwned(p.uid(),id);
        return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }
}
