package com.harvey.digitalgarden.tracker;

import com.harvey.digitalgarden.common.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracker/goals")
public class TrackerGoalController {
  final TrackerGoalService goals;

  public TrackerGoalController(TrackerGoalService g) {
    goals = g;
  }

  @PostMapping("/natural")
  public Result<?> natural(
      @AuthenticationPrincipal TrackerPrincipal user,
      @RequestBody TrackerTypes.NaturalInput input) {
    return Result.success(goals.create(user.id(), input));
  }
}
