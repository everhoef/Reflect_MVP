package direct.reflect.facilitator.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WebConfig {

    @RequestMapping(value = {"/login", "/retro/**"})
    public String spa() {
        return "forward:/index.html";
    }
}
