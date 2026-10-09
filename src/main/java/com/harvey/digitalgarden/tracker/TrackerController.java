package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import com.harvey.digitalgarden.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracker")
public class TrackerController {
  final TrackerClientAddress addresses;
  final TrackerAuthService auth;
  final TrackerActivityService activities;
  final TrackerRecordService records;
  final TrackerTimerService timers;

  public TrackerController(
      TrackerAuthService a,
      TrackerActivityService b,
      TrackerRecordService c,
      TrackerTimerService d,
      TrackerClientAddress addresses) {
    this.addresses = addresses;
    auth = a;
    activities = b;
    records = c;
    timers = d;
  }

  public record TimezoneInput(String timezone) {}

  public record ArchiveInput(Boolean archived) {}

  public record DeletedInput(Boolean deleted) {}

  public record RequestInput(UUID requestId) {}

  @PostMapping("/auth/register")
  public Result<?> register(@RequestBody Credentials c, HttpServletRequest request) {
    auth.throttle(addresses.resolve(request));
    return Result.success(auth.register(c));
  }

  @PostMapping("/auth/login")
  public Result<?> login(@RequestBody Credentials c, HttpServletRequest request) {
    auth.throttle(addresses.resolve(request));
    return Result.success(auth.login(c));
  }

  @GetMapping("/auth/me")
  public Result<?> me(@AuthenticationPrincipal TrackerPrincipal p) {
    return Result.success(auth.me(p.id()));
  }

  @PutMapping("/auth/me")
  public Result<?> timezone(
      @AuthenticationPrincipal TrackerPrincipal p, @RequestBody TimezoneInput input) {
    return Result.success(auth.timezone(p.id(), input.timezone()));
  }

  @GetMapping("/activities")
  public Result<?> activities(
      @AuthenticationPrincipal TrackerPrincipal p,
      @RequestParam(required = false) LocalDate date,
      @RequestParam(defaultValue = "WEEK") Period period,
      @RequestParam(defaultValue = "false") boolean archived) {
    return Result.success(activities.list(p.id(), date, period, archived));
  }

  @PostMapping("/activities")
  public Result<?> create(
      @AuthenticationPrincipal TrackerPrincipal p, @RequestBody ActivityInput input) {
    return Result.success(activities.create(p.id(), input));
  }

  @PutMapping("/activities/{id}")
  public Result<?> edit(
      @AuthenticationPrincipal TrackerPrincipal p,
      @PathVariable long id,
      @RequestBody ActivityEdit input) {
    return Result.success(activities.edit(p.id(), id, input));
  }

  @PatchMapping("/activities/{id}/archive")
  public Result<?> archive(
      @AuthenticationPrincipal TrackerPrincipal p,
      @PathVariable long id,
      @RequestBody ArchiveInput input) {
    if (input.archived() == null) throw TrackerException.bad("缺少归档状态");
    activities.archive(p.id(), id, input.archived());
    return Result.success();
  }

  @GetMapping("/activities/{id}/records")
  public Result<?> records(
      @AuthenticationPrincipal TrackerPrincipal p,
      @PathVariable long id,
      @RequestParam(required = false) LocalDate date,
      @RequestParam(defaultValue = "WEEK") Period period) {
    return Result.success(records.list(p.id(), id, date, period));
  }

  @PostMapping("/activities/{id}/records")
  public Result<?> record(
      @AuthenticationPrincipal TrackerPrincipal p,
      @PathVariable long id,
      @RequestBody RecordInput input) {
    return Result.success(records.create(p.id(), id, input));
  }

  @PutMapping("/records/{id}")
  public Result<?> editRecord(
      @AuthenticationPrincipal TrackerPrincipal p,
      @PathVariable long id,
      @RequestBody RecordInput input) {
    return Result.success(records.edit(p.id(), id, input));
  }

  @PatchMapping("/records/{id}/deleted")
  public Result<?> deleted(
      @AuthenticationPrincipal TrackerPrincipal p,
      @PathVariable long id,
      @RequestBody DeletedInput input) {
    if (input.deleted() == null) throw TrackerException.bad("缺少删除状态");
    records.deleted(p.id(), id, input.deleted());
    return Result.success();
  }

  @GetMapping("/timer")
  public Result<?> timer(@AuthenticationPrincipal TrackerPrincipal p) {
    return Result.success(timers.get(p.id()));
  }

  @PostMapping("/activities/{id}/timer")
  public Result<?> start(
      @AuthenticationPrincipal TrackerPrincipal p,
      @PathVariable long id,
      @RequestBody RequestInput input) {
    return Result.success(timers.start(p.id(), id, input.requestId()));
  }

  @PostMapping("/timer/{id}/pause")
  public Result<?> pause(@AuthenticationPrincipal TrackerPrincipal p, @PathVariable long id) {
    return Result.success(timers.transition(p.id(), id, false));
  }

  @PostMapping("/timer/{id}/resume")
  public Result<?> resume(@AuthenticationPrincipal TrackerPrincipal p, @PathVariable long id) {
    return Result.success(timers.transition(p.id(), id, true));
  }

  @PostMapping("/timer/{id}/finish")
  public Result<?> finish(@AuthenticationPrincipal TrackerPrincipal p, @PathVariable long id) {
    return Result.success(timers.finish(p.id(), id));
  }

  @PostMapping("/timer/{id}/cancel")
  public Result<?> cancel(@AuthenticationPrincipal TrackerPrincipal p, @PathVariable long id) {
    timers.cancel(p.id(), id);
    return Result.success();
  }
}
