package direct.reflect.facilitator;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Facilitator", version = "1.0", description = "Retrospective facilitation platform API"))
public final class FacilitatorApplication {

    private FacilitatorApplication() {
        // Utility class / Spring Boot entry point
    }

    public static void main(String[] args) {
        SpringApplication.run(FacilitatorApplication.class, args);
    }

}
