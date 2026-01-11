import tensorflow as tf
import numpy as np
import json
import cv2
import sys

def predict_image(image_path):
    """Load model and predict food"""
    
    # Load model
    loaded = tf.saved_model.load('saved_model')
    infer = loaded.signatures['serving_default']
    
    # Load class names
    with open('food41_classes.json', 'r') as f:
        class_names = json.load(f)
    
    # Preprocess image
    img = cv2.imread(image_path)
    if img is None:
        print("Error: Could not read image")
        sys.exit(1)
    
    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    img = cv2.resize(img, (224, 224))
    img_array = np.expand_dims(img, 0).astype(np.float32)
    img_array = tf.keras.applications.efficientnet.preprocess_input(img_array)
    
    # Predict
    output_key = list(infer.structured_outputs.keys())[0]
    result = infer(tf.constant(img_array))
    predictions = result[output_key].numpy()[0]
    
    # Get top prediction
    top_idx = np.argmax(predictions)
    top_class = class_names[top_idx]
    top_confidence = predictions[top_idx] * 100
    
    # Print result in format that Java can parse
    print(f"Top prediction: {top_class} ({top_confidence:.1f}%)")
    
    # Print all top 5 predictions
    print("All predictions:")
    top_indices = np.argsort(predictions)[-5:][::-1]
    for i, idx in enumerate(top_indices, 1):
        print(f"{i}. {class_names[idx]}: {predictions[idx]*100:.1f}%")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Error: No image path provided")
        sys.exit(1)
    
    try:
        predict_image(sys.argv[1])
    except Exception as e:
        print(f"Error: {str(e)}")
        sys.exit(1)