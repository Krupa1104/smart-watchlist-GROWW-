package com.groww.smart_watchlist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling powers TickSimulationService's @Scheduled tick() —
// the DEMO simulated intraday feed (see that class). Nothing else in the
// app uses scheduling; added here rather than a separate config class
// since this is a one-line addition to the existing entry point.
@SpringBootApplication
@EnableScheduling
public class SmartWatchlistApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartWatchlistApplication.class, args);
	}

}
