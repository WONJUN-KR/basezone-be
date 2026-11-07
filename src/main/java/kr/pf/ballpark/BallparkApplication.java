package kr.pf.ballpark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BallparkApplication {

	public static void main(String[] args) {
		SpringApplication.run(BallparkApplication.class, args);
	}

}
