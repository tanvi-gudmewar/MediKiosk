package com.medikiosk.backend;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class PatientController {

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository; 

    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/ask-followup")
public Map<String, Object> askFollowup(@RequestBody Map<String, String> body) throws Exception {

    String symptoms = body.get("symptoms");

    if (symptoms == null || symptoms.trim().isEmpty()) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Symptoms field cannot be empty");
        return error;
    }

    // ... rest of the existing code

        String prompt = "A patient described these symptoms: \"" + symptoms + "\". " +
            "As a medical assistant, generate 3 short, relevant follow-up questions " +
            "to better understand their condition. " +
            "Return ONLY a JSON array of strings, like: " +
            "[\"question 1\", \"question 2\", \"question 3\"] " +
            "No extra text, no markdown formatting, no backticks.";

        String rawResponse = geminiService.generateContent(prompt);

        // Clean up in case Gemini adds markdown formatting
       String cleaned = rawResponse.replace("```json", "").replace("```", "").trim();

List<String> questions;
try {
    questions = mapper.readValue(cleaned, List.class);
} catch (Exception e) {
    // Fallback if AI didn't return valid JSON
    questions = List.of(
        "Can you describe when your symptoms started?",
        "Have you experienced this before?",
        "Are you currently taking any medication?"
    );
}

Map<String, Object> result = new HashMap<>();
result.put("questions", questions);
return result;
    }
    @PostMapping("/generate-summary")
public Map<String, Object> generateSummary(@RequestBody Map<String, Object> body) throws Exception {

    Map<String, String> patientData = (Map<String, String>) body.get("patientData");
    List<Map<String, String>> followupAnswers = (List<Map<String, String>>) body.get("followupAnswers");
    Map<String, Object> documentInfo = (Map<String, Object>) body.get("documentInfo");
String documentSummary = documentInfo != null ? (String) documentInfo.get("documentSummary") : null;
String documentPath = documentInfo != null ? (String) documentInfo.get("documentPath") : null;

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
    (documentSummary != null ? "Old Medical Document Summary:\n" + documentSummary + "\n\n" : "") +
    "Format the summary with these exact sections:\n" +
    "- Chief Complaint\n" +
    "- History of Present Illness (combine all the above info into a short paragraph)\n" +
    "- Patient Demographics\n" +
    (documentSummary != null ? "- Relevant Medical History (from uploaded document)\n" : "") +
    "\nReturn plain text only, no markdown, no asterisks.";

    String summary = geminiService.generateContent(prompt);

    // Save to MySQL
    Patient patient = new Patient();
    patient.setName(patientData.get("name"));
    patient.setAge(Integer.parseInt(patientData.get("age")));
    patient.setGender(patientData.get("gender"));
    patient.setSymptoms(patientData.get("symptoms"));
    patient.setFollowupAnswers(mapper.writeValueAsString(followupAnswers));
    patient.setSummary(summary);
    patient.setDocumentPath(documentPath);
    patient.setDocumentSummary(documentSummary);

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
@PostMapping("/login")
public Map<String, Object> login(@RequestBody Map<String, String> body) {
    String username = body.get("username");
    String password = body.get("password");

    Map<String, Object> result = new HashMap<>();

    Optional<Doctor> doctor = doctorRepository.findByUsername(username);

    if (doctor.isPresent() && doctor.get().getPassword().equals(password)) {
        result.put("success", true);
    } else {
        result.put("success", false);
        result.put("message", "Invalid username or password");
    }

    return result;
}

   @PostMapping("/upload-document")
public Map<String, Object> uploadDocument(@RequestParam("file") MultipartFile file) throws Exception {

    Map<String, Object> result = new HashMap<>();

    if (file.isEmpty()) {
        result.put("error", "No file uploaded");
        return result;
    }

    // Save file to local "uploads" folder
    String uploadDir = "uploads/";
    Files.createDirectories(Paths.get(uploadDir));

    String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
    Path filePath = Paths.get(uploadDir + filename);
    Files.write(filePath, file.getBytes());

    // Determine mime type (image or pdf)
    String mimeType = file.getContentType();

    String prompt = "This is a patient's old medical document (prescription or report). " +
        "Extract and summarize the key medical information: diagnosis, medications, " +
        "dosages, and any important notes. Keep it concise and in plain text.";

    String extractedSummary = geminiService.generateContentWithFile(prompt, file.getBytes(), mimeType);

    result.put("documentPath", filePath.toString());
    result.put("documentSummary", extractedSummary);
    return result;
}

   @GetMapping("/uploads/{filename:.+}")
public ResponseEntity<Resource> getUploadedFile(@PathVariable String filename) throws Exception {
    Path filePath = Paths.get("uploads/").resolve(filename);
    System.out.println("Looking for file at: " + filePath.toAbsolutePath());

    Resource resource = new UrlResource(filePath.toUri());

    if (!resource.exists()) {
        return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(resource);
}
}