package direct.reflect.facilitator.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class WebConfig {

    @RequestMapping(
            value = {"/", "/login", "/home", "/retro/**"},
            method = RequestMethod.GET,
            produces = "text/html")
    public String spa() {
        return "forward:/index.html";
    }
}
