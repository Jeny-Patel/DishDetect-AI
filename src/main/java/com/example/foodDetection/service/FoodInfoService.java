package com.example.foodDetection.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class FoodInfoService {

    @Value("${spoonacular.api.key}")
    private String apiKey;

    @Value("${spoonacular.api.base-url:https://api.spoonacular.com}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FoodInfo getFoodInfo(String foodName) {
        try {
            String cleanName = foodName.toLowerCase().replace("_", " ").trim();
            
            System.out.println("Searching Spoonacular recipes for: " + cleanName);
            
            // Use Recipe Search API instead (better for dishes)
            String encodedName = URLEncoder.encode(cleanName, StandardCharsets.UTF_8);
            String searchUrl = String.format(
                "%s/recipes/complexSearch?query=%s&number=1&addRecipeInformation=true&fillIngredients=true&apiKey=%s",
                baseUrl, encodedName, apiKey
            );
            
            String searchResponse = restTemplate.getForObject(searchUrl, String.class);
            JsonNode searchResult = objectMapper.readTree(searchResponse);
            
            // Check if we found results
            if (!searchResult.has("results") || searchResult.get("results").size() == 0) {
                System.out.println("No recipes found, using fallback");
                return getFallbackData(foodName);
            }
            
            JsonNode recipe = searchResult.get("results").get(0);
            
            System.out.println(" Found recipe: " + recipe.get("title").asText());
            
            // Parse the recipe data
            return parseRecipeData(recipe, cleanName);
            
        } catch (HttpClientErrorException e) {
            System.err.println("Spoonacular API Error: " + e.getStatusCode());
            if (e.getStatusCode().value() == 402) {
                System.err.println("Daily quota exceeded (150 requests/day)");
            }
            return getFallbackData(foodName);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return getFallbackData(foodName);
        }
    }

    private FoodInfo parseRecipeData(JsonNode recipe, String originalName) {
        FoodInfo info = new FoodInfo();
        
        // Basic info
        info.setName(recipe.has("title") ? recipe.get("title").asText() : originalName);
        
        // Description
        if (recipe.has("summary")) {
            String summary = recipe.get("summary").asText();
            // Remove HTML tags
            summary = summary.replaceAll("<[^>]*>", "");
            // Limit to 200 characters
            if (summary.length() > 200) {
                summary = summary.substring(0, 197) + "...";
            }
            info.setDescription(summary);
        } else {
            info.setDescription("A delicious " + originalName + " dish");
        }
        
        // Parse ingredients
        List<String> ingredients = new ArrayList<>();
        if (recipe.has("extendedIngredients")) {
            JsonNode ingredientsArray = recipe.get("extendedIngredients");
            for (JsonNode ingredient : ingredientsArray) {
                String name = ingredient.get("original").asText();
                ingredients.add(name);
            }
        }
        
        if (ingredients.isEmpty()) {
            ingredients.add("Ingredient information not available");
        }
        info.setIngredients(ingredients);
        
        // Nutrition
        if (recipe.has("nutrition") && recipe.get("nutrition").has("nutrients")) {
            JsonNode nutrients = recipe.get("nutrition").get("nutrients");
            for (JsonNode nutrient : nutrients) {
                if (nutrient.get("name").asText().equals("Calories")) {
                    info.setCalories((int) nutrient.get("amount").asDouble());
                    break;
                }
            }
        }
        
        // Dietary properties from Spoonacular
        if (recipe.has("vegan")) {
            info.setVegan(recipe.get("vegan").asBoolean());
        }
        if (recipe.has("vegetarian")) {
            info.setVegetarian(recipe.get("vegetarian").asBoolean());
        }
        if (recipe.has("glutenFree")) {
            info.setGlutenFree(recipe.get("glutenFree").asBoolean());
        }
        if (recipe.has("dairyFree")) {
            info.setDairyFree(recipe.get("dairyFree").asBoolean());
        }
        
        // Detect allergens from ingredients
        info.setAllergens(detectAllergensFromIngredients(ingredients));
        
        System.out.println("Parsed: " + info.getName() + " with " + ingredients.size() + " ingredients");
        
        return info;
    }

    private List<String> detectAllergensFromIngredients(List<String> ingredients) {
        Set<String> allergens = new HashSet<>();
        String allIngredients = String.join(" ", ingredients).toLowerCase();
        
        if (allIngredients.contains("milk") || allIngredients.contains("cheese") || 
            allIngredients.contains("cream") || allIngredients.contains("butter") ||
            allIngredients.contains("yogurt")) {
            allergens.add("Dairy");
        }
        
        if (allIngredients.contains("egg")) {
            allergens.add("Eggs");
        }
        
        if (allIngredients.contains("wheat") || allIngredients.contains("flour") ||
            allIngredients.contains("bread")) {
            allergens.add("Gluten");
        }
        
        if (allIngredients.contains("fish") || allIngredients.contains("salmon") ||
            allIngredients.contains("tuna") || allIngredients.contains("seafood")) {
            allergens.add("Fish/Seafood");
        }
        
        if (allIngredients.contains("soy") || allIngredients.contains("tofu")) {
            allergens.add("Soy");
        }
        
        if (allIngredients.contains("peanut") || allIngredients.contains("almond") ||
            allIngredients.contains("walnut") || allIngredients.contains("cashew") ||
            allIngredients.contains("nut")) {
            allergens.add("Tree Nuts");
        }
        
        if (allIngredients.contains("shrimp") || allIngredients.contains("crab") ||
            allIngredients.contains("lobster") || allIngredients.contains("shellfish")) {
            allergens.add("Shellfish");
        }
        
        return new ArrayList<>(allergens);
    }

    private FoodInfo getFallbackData(String foodName) {
        System.out.println("Using fallback data for: " + foodName);
        
        FoodInfo info = new FoodInfo();
        String nameLower = foodName.toLowerCase().replace(" ", "_");
        
        switch (nameLower) {
            case "pizza":
                info.setName("Pizza");
                info.setIngredients(Arrays.asList(
                    "1 lb pizza dough",
                    "1/2 cup tomato sauce",
                    "2 cups mozzarella cheese",
                    "2 tbsp olive oil",
                    "Fresh basil leaves",
                    "Salt and pepper to taste"
                ));
                info.setVegetarian(true);
                info.setCalories(266);
                info.setAllergens(Arrays.asList("Gluten", "Dairy"));
                info.setDescription("Classic Italian flatbread topped with tomato sauce, cheese, and various toppings, baked until golden and bubbly");
                break;
                
            case "hamburger":
                info.setName("Hamburger");
                info.setIngredients(Arrays.asList(
                    "1 lb ground beef",
                    "4 hamburger buns",
                    "4 slices cheese",
                    "Lettuce leaves",
                    "Tomato slices",
                    "Onion slices",
                    "Pickles",
                    "Ketchup and mustard"
                ));
                info.setVegetarian(false);
                info.setCalories(540);
                info.setAllergens(Arrays.asList("Gluten", "Dairy"));
                info.setDescription("Classic American sandwich with seasoned ground beef patty, fresh vegetables, and condiments between soft buns");
                break;
                
            case "sushi":
                info.setName("Sushi");
                info.setIngredients(Arrays.asList(
                    "2 cups sushi rice",
                    "1/4 cup rice vinegar",
                    "8 oz fresh fish (tuna, salmon)",
                    "4 nori sheets",
                    "Soy sauce",
                    "Wasabi",
                    "Pickled ginger",
                    "Cucumber and avocado"
                ));
                info.setVegetarian(false);
                info.setGlutenFree(true);
                info.setDairyFree(true);
                info.setCalories(143);
                info.setAllergens(Arrays.asList("Fish", "Soy"));
                info.setDescription("Traditional Japanese dish featuring vinegared rice combined with fresh raw fish, vegetables, and seaweed");
                break;
                
            case "donuts":
            case "donut":
                info.setName("Donuts");
                info.setIngredients(Arrays.asList(
                    "2 cups all-purpose flour",
                    "1/2 cup sugar",
                    "2 eggs",
                    "1 cup milk",
                    "1/4 cup butter",
                    "2 tsp yeast",
                    "1 tsp vanilla extract",
                    "Oil for frying",
                    "Glaze or icing"
                ));
                info.setVegetarian(true);
                info.setCalories(250);
                info.setAllergens(Arrays.asList("Gluten", "Dairy", "Eggs"));
                info.setDescription("Sweet fried dough confection, often ring-shaped, glazed or filled with cream or jam");
                break;
                
            case "french_fries":
            case "french fries":
                info.setName("French Fries");
                info.setIngredients(Arrays.asList(
                    "4 large potatoes",
                    "Vegetable oil for frying",
                    "Salt to taste"
                ));
                info.setVegan(true);
                info.setVegetarian(true);
                info.setGlutenFree(true);
                info.setDairyFree(true);
                info.setCalories(312);
                info.setAllergens(new ArrayList<>());
                info.setDescription("Crispy deep-fried potato strips, golden on the outside and fluffy inside, seasoned with salt");
                break;
                
            case "ice_cream":
            case "ice cream":
                info.setName("Ice Cream");
                info.setIngredients(Arrays.asList(
                    "2 cups heavy cream",
                    "1 cup whole milk",
                    "3/4 cup sugar",
                    "4 egg yolks",
                    "2 tsp vanilla extract",
                    "Pinch of salt"
                ));
                info.setVegetarian(true);
                info.setGlutenFree(true);
                info.setCalories(207);
                info.setAllergens(Arrays.asList("Dairy", "Eggs"));
                info.setDescription("Frozen dessert made from sweetened and flavored dairy products, churned to create a smooth, creamy texture");
                break;
                
            case "steak":
                info.setName("Steak");
                info.setIngredients(Arrays.asList(
                    "1 lb beef steak (ribeye or sirloin)",
                    "2 tbsp butter",
                    "3 cloves garlic",
                    "Fresh rosemary and thyme",
                    "Salt and black pepper",
                    "Olive oil"
                ));
                info.setVegetarian(false);
                info.setGlutenFree(true);
                info.setCalories(679);
                info.setAllergens(Arrays.asList("Dairy"));
                info.setDescription("Premium cut of beef, grilled or pan-seared to perfection, seasoned with herbs and spices");
                break;
                
            case "ramen":
                info.setName("Ramen");
                info.setIngredients(Arrays.asList(
                    "4 oz wheat noodles",
                    "4 cups chicken or pork broth",
                    "2 tbsp soy sauce",
                    "1 tbsp miso paste",
                    "2 soft-boiled eggs",
                    "4 oz pork belly or chicken",
                    "Green onions",
                    "Nori sheets",
                    "Bamboo shoots"
                ));
                info.setVegetarian(false);
                info.setDairyFree(true);
                info.setCalories(436);
                info.setAllergens(Arrays.asList("Gluten", "Eggs", "Soy"));
                info.setDescription("Japanese noodle soup with rich broth, topped with meat, eggs, and vegetables");
                break;
                
            default:
                info.setName(foodName.replace("_", " "));
                info.setIngredients(Arrays.asList("Ingredient information not available"));
                info.setCalories(0);
                info.setAllergens(new ArrayList<>());
                info.setDescription("Nutritional information unavailable for this food item");
        }
        
        return info;
    }

    public static class FoodInfo {
        private String name;
        private String description;
        private List<String> ingredients;
        private boolean vegan;
        private boolean vegetarian;
        private boolean glutenFree;
        private boolean dairyFree;
        private List<String> allergens;
        private int calories;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getIngredients() { return ingredients; }
        public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }
        public boolean isVegan() { return vegan; }
        public void setVegan(boolean vegan) { this.vegan = vegan; }
        public boolean isVegetarian() { return vegetarian; }
        public void setVegetarian(boolean vegetarian) { this.vegetarian = vegetarian; }
        public boolean isGlutenFree() { return glutenFree; }
        public void setGlutenFree(boolean glutenFree) { this.glutenFree = glutenFree; }
        public boolean isDairyFree() { return dairyFree; }
        public void setDairyFree(boolean dairyFree) { this.dairyFree = dairyFree; }
        public List<String> getAllergens() { return allergens; }
        public void setAllergens(List<String> allergens) { this.allergens = allergens; }
        public int getCalories() { return calories; }
        public void setCalories(int calories) { this.calories = calories; }
        
        public List<String> getDietaryTags() {
            List<String> tags = new ArrayList<>();
            if (vegan) tags.add("Vegan");
            if (vegetarian) tags.add("Vegetarian");
            if (glutenFree) tags.add("Gluten-Free");
            if (dairyFree) tags.add("Dairy-Free");
            return tags;
        }
    }
}