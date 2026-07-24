package com.interview.platform.question;

import com.interview.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/jd")
class JdController {
    private final JdService service;
    JdController(JdService service) { this.service = service; }
    @PostMapping("/analyze") ApiResponse<JdService.Analysis> analyze(@RequestBody JdService.Command request) {
        return ApiResponse.ok(service.analyze(request.jdText()));
    }
}
