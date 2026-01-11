package com.example.foodDetection.controller;

import com.example.foodDetection.service.FoodInfoService;
import com.example.foodDetection.service.FoodInfoService.FoodInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class FoodController {

    @Autowired
    private FoodInfoService foodInfoService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/upload")
    public String uploadImage(MultipartFile image, Model model) throws Exception {
        
        if (image.isEmpty()) {
            model.addAttribute("food", "No image uploaded");
            model.addAttribute("confidence", "0%");
            model.addAttribute("error", true);
            return "result";
        }

        // Save uploaded image temporarily
        Path tempFile = Files.createTempFile("food_", ".jpg");
        try (InputStream is = image.getInputStream()) {
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            // Call Python script to get prediction
            String prediction = callPythonPredictor(tempFile.toString());
            
            // Parse the prediction result
            PredictionResult result = parsePrediction(prediction);
            
            System.out.println("Detected food: " + result.foodName);
            
            // Get detailed food information from service
            FoodInfo foodInfo = foodInfoService.getFoodInfo(result.foodName);
            
            System.out.println("Got food info: " + foodInfo.getName());
            System.out.println("Ingredients count: " + foodInfo.getIngredients().size());
            System.out.println("Description: " + foodInfo.getDescription());
            
            // Add all attributes to model for Thymeleaf
            model.addAttribute("food", foodInfo.getName());
            model.addAttribute("confidence", String.format("%.1f%%", result.confidence));
            model.addAttribute("allPredictions", result.allPredictions);
            model.addAttribute("error", false);
            
            // Add food info attributes
            model.addAttribute("description", foodInfo.getDescription());
            model.addAttribute("ingredients", foodInfo.getIngredients());
            model.addAttribute("dietaryTags", foodInfo.getDietaryTags());
            model.addAttribute("allergens", foodInfo.getAllergens());
            model.addAttribute("calories", foodInfo.getCalories());
            model.addAttribute("isVegan", foodInfo.isVegan());
            model.addAttribute("isVegetarian", foodInfo.isVegetarian());
            model.addAttribute("isGlutenFree", foodInfo.isGlutenFree());
            model.addAttribute("isDairyFree", foodInfo.isDairyFree());
            
            // Debug output
            System.out.println("Model attributes set:");
            System.out.println("  - description: " + foodInfo.getDescription());
            System.out.println("  - ingredients: " + foodInfo.getIngredients());
            System.out.println("  - calories: " + foodInfo.getCalories());
            
        } catch (Exception e) {
            System.err.println("Error during prediction: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("food", "Error: " + e.getMessage());
            model.addAttribute("confidence", "0%");
            model.addAttribute("error", true);
        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempFile);
        }

        return "result";
    }

    private String callPythonPredictor(String imagePath) throws IOException, InterruptedException {
        // Build command to call Python script
        ProcessBuilder pb = new ProcessBuilder("python", "predict_spring.py", imagePath);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Read output
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream())
        );
        
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Python script failed: " + output.toString());
        }
        
        return output.toString();
    }

    private PredictionResult parsePrediction(String output) {
        PredictionResult result = new PredictionResult();
        result.allPredictions = new ArrayList<>();
        
        String[] lines = output.trim().split("\n");
        
        for (String line : lines) {
            // Extract top prediction
            if (line.startsWith("Top prediction:")) {
                Pattern pattern = Pattern.compile("Top prediction: (.+?) \\((\\d+\\.\\d+)%\\)");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    result.foodName = matcher.group(1).replace("_", " ");
                    result.confidence = Double.parseDouble(matcher.group(2));
                }
            }
            
            // Extract all predictions
            Pattern allPattern = Pattern.compile("\\d+\\. (.+?): (\\d+\\.\\d+)%");
            Matcher allMatcher = allPattern.matcher(line);
            if (allMatcher.find()) {
                String name = allMatcher.group(1).replace("_", " ");
                double conf = Double.parseDouble(allMatcher.group(2));
                result.allPredictions.add(name + " (" + String.format("%.1f%%", conf) + ")");
            }
        }
        
        // Fallback if parsing failed
        if (result.foodName == null) {
            result.foodName = "Unknown";
            result.confidence = 0.0;
        }
        
        return result;
    }

    // Inner class to hold prediction results
    private static class PredictionResult {
        String foodName;
        double confidence;
        List<String> allPredictions;
    }
}
