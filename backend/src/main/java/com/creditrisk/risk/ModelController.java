package com.creditrisk.risk;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes which model is scoring applications and how well it performed on held-out data. */
@RestController
@RequestMapping("/api/model")
public class ModelController {

    public record ModelInfo(String modelVersion, String algorithm, String trainedOn,
                            RiskModel.Metrics metrics, RiskModel.Thresholds thresholds,
                            double baselineProbability) {
    }

    private final RiskScoringService scoringService;

    public ModelController(RiskScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @GetMapping
    public ModelInfo info() {
        RiskModel model = scoringService.model();
        RiskModel.Definition d = model.definition();
        return new ModelInfo(d.modelVersion(), d.algorithm(), d.trainedOn(), d.metrics(), d.thresholds(),
                model.baselineProbability());
    }
}
