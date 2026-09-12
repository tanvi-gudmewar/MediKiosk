package com.medikiosk.backend;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class PatientController {

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private PatientRepository patientRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/ask-followup")
    public Map<String, Object> askFollowup(@RequestBody Map<String, String> body) throws Exception {

        String symptoms = body.get("symptoms");

        String prompt = "A patient described these symptoms: \"" + symptoms + "\". " +
            "As a medical assistant, generate 3 short, relevant follow-up questions " +
            "to better understand their condition. " +
            "Return ONLY a JSON array of strings, like: " +
            "[\"question 1\", \"question 2\", \"question 3\"] " +
            "No extra text, no markdown formatting, no backticks.";

        String rawResponse = geminiService.generateContent(prompt);

        // Clean up in case Gemini adds markdown formatting
        String cleaned = rawResponse.replace("```json", "").replace("```", "").trim();

        List<String> questions = mapper.readValue(cleaned, List.class);

        Map<String, Object> result = new HashMap<>();
        result.put("questions", questions);
        return result;
    }
    @PostMapping("/generate-summary")
public Map<String, Object> generateSummary(@RequestBody Map<String, Object> body) throws Exception {

    Map<String, String> patientData = (Map<String, String>) body.get("patientData");
    List<Map<String, String>> followupAnswers = (List<Map<String, String>>) body.get("followupAnswers");

    StringBuilder qaText = new StringBuilder();
    for (int i = 0; i < followupAnswers.size(); i++) {
        Map<String, String> qa = followupAnswers.get(i);
        qaText.append((i + 1)).append(". Q: ").append(qa.get("question"))
              .append("\n   A: ").append(qa.get("answer")).append("\n");
    }

    String prompt = "Create a structured medical case summary for a doctor, based on this patient intake:\n\n" +
        "Name: " + patientData.get("name") + "\n" +
        "Age: " + patientData.get("age") + "\n" +
        "Gender: " + patientData.get("gender") + "\n" +
        "Chief Complaint: " + patientData.get("symptoms") + "\n\n" +
        "Follow-up Q&A:\n" + qaText.toString() + "\n\n" +
        "Format the summary with these exact sections:\n" +
        "- Chief Complaint\n" +
        "- History of Present Illness (combine all the above info into a short paragraph)\n" +
        "- Patient Demographics\n\n" +
        "Return plain text only, no markdown, no asterisks.";

    String summary = geminiService.generateContent(prompt);

    // Save to MySQL
    Patient patient = new Patient();
    patient.setName(patientData.get("name"));
    patient.setAge(Integer.parseInt(patientData.get("age")));
    patient.setGender(patientData.get("gender"));
    patient.setSymptoms(patientData.get("symptoms"));
    patient.setFollowupAnswers(mapper.writeValueAsString(followupAnswers));
    patient.setSummary(summary);

    patientRepository.save(patient);

    Map<String, Object> result = new HashMap<>();
    result.put("summary", summary);
    return result;
}

@GetMapping("/patients")
public Map<String, Object> getAllPatients() {
    List<Patient> patients = patientRepository.findAll();
    Map<String, Object> result = new HashMap<>();
    result.put("patients", patients);
    return result;
}
}