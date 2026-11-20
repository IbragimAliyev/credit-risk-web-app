package com.creditrisk.application;

import com.creditrisk.application.LoanApplicationDtos.CreateRequest;
import com.creditrisk.application.LoanApplicationDtos.Response;
import com.creditrisk.exception.NotFoundException;
import com.creditrisk.risk.ApplicantProfile;
import com.creditrisk.risk.RiskAssessment;
import com.creditrisk.risk.RiskScoringService;
import com.creditrisk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LoanApplicationService {

    private final LoanApplicationRepository repository;
    private final UserRepository userRepository;
    private final RiskScoringService scoringService;

    public LoanApplicationService(LoanApplicationRepository repository, UserRepository userRepository,
                                  RiskScoringService scoringService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.scoringService = scoringService;
    }

    @Transactional
    public Response create(Long userId, CreateRequest req) {
        ApplicantProfile profile = req.toProfile();
        RiskAssessment assessment = scoringService.assess(profile);

        LoanApplication app = new LoanApplication(
                userRepository.getReferenceById(userId),
                req.applicantName().trim(),
                profile,
                assessment);

        return Response.from(repository.save(app));
    }

    @Transactional(readOnly = true)
    public List<Response> listForUser(Long userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Response getForUser(Long userId, Long id) {
        // Scoped to the owner: other users' applications are reported as not found.
        return repository.findByIdAndUserId(id, userId)
                .map(Response::from)
                .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
    }
}
