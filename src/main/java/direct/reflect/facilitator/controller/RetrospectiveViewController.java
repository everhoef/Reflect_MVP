package direct.reflect.facilitator.controller;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import direct.reflect.facilitator.service.RetrospectiveService;
import direct.reflect.facilitator.dto.RetrospectiveSessionJoinDTO;
import direct.reflect.facilitator.dto.RetrospectiveSessionResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

@Controller
public class RetrospectiveViewController {

  private static final Logger log = LoggerFactory.getLogger(RetrospectiveViewController.class);

  @Autowired
  private RetrospectiveService retrospectiveService;

  @GetMapping({"/", "/retrospective"})
  public String home(Model model) {
    addAttributesToModel(model, Map.of("page", "home", "title", "Reflect.Direct Facilitator"));
    return "layout";
  }

  @PostMapping("/retrospective/create")
  public String createRetrospective(@RequestParam(required = false) String username,
      @RequestParam String sessionName) {
    String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
    RetrospectiveSessionResponseDTO sessionDTO = retrospectiveService.createSession(currentUser);
    return "redirect:/retrospective/" + sessionDTO.getId();
  }

  @PostMapping("/retrospective/join")
  public String joinRetrospective(@RequestParam(required = false) String username,
      @RequestParam UUID retroId) {
    String currentUser = username != null ? username
        : SecurityContextHolder.getContext().getAuthentication().getName();
    RetrospectiveSessionJoinDTO joinDTO = new RetrospectiveSessionJoinDTO();
    joinDTO.setId(retroId);
    joinDTO.setUsername(currentUser);
    RetrospectiveSessionResponseDTO responseDTO = retrospectiveService.joinSession(joinDTO);
    return "redirect:/retrospective/" + responseDTO.getId();
  }

  @GetMapping("/retrospective/{retroId}")
  public String retrospectiveLobby(@PathVariable UUID retroId, Model model) {
    String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
    List<String> participants = retrospectiveService.getParticipants(retroId);

    addAttributesToModel(model,
        Map.of("page", "lobby", "userName", currentUser, "retroId", retroId, "title",
            "Retrospective", "participants", participants, "isFacilitator",
            participants.get(0).equals(currentUser)));
    return "layout";
  }

  @PostMapping("/retrospective/{retroId}/start")
  public String startSession(@PathVariable UUID retroId) {
    retrospectiveService.startSession(retroId);
    return "redirect:/retrospective/" + retroId + "/session";
  }

  @GetMapping("/retrospective/{retroId}/session")
  public String retrospectiveSession(@PathVariable UUID retroId, Model model) {
    String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
    List<String> participants = retrospectiveService.getParticipants(retroId);

    addAttributesToModel(model, Map.of("page", "retro", "retroId", retroId, "title",
        "Retrospective Session", "isFacilitator", participants.get(0).equals(currentUser)));
    return "layout";
  }

  @PostMapping("/retrospective/{retroId}/next")
  @ResponseBody
  public void nextPage(@PathVariable UUID retroId) {
    // Handle next page logic and emit SSE event
    // Will be implemented later
  }

  @GetMapping(path = "/retrospective/{retroId}/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribeToEvents(@PathVariable UUID retroId, @RequestParam String username, // Make
                                                                                                 // username
                                                                                                 // required
      @RequestParam(required = false) String lastEventId) {
    log.debug("SSE subscription request - retroId: {}, username: {}", retroId, username);
    return retrospectiveService.subscribe(retroId, username, lastEventId);
  }

  @GetMapping("/retrospective/{retroId}/participants")
  public String getParticipantsList(@PathVariable UUID retroId, Model model,
      @RequestParam(required = false) String trigger) {
    // Log the trigger to understand when this endpoint is called
    log.debug("Participants list requested for session {} with trigger {}", retroId, trigger);
    List<String> participants = retrospectiveService.getParticipants(retroId);
    model.addAttribute("participants", participants);
    return "fragments/lobby :: ul.space-y-2";
  }

  private void addAttributesToModel(Model model, Map<String, Object> attributes) {
    attributes.forEach(model::addAttribute);
  }
}
