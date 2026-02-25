import os
import sys
import cv2
from ascii_magic import AsciiArt
from PIL import Image
import numpy as np
import tempfile

def load_image(image_path):
    """Load image using OpenCV"""
    print(f"Loading image from {image_path}...")
    img = cv2.imread(image_path)
    if img is None:
        print(f"Error: Could not load image from {image_path}")
        return None
    return img

def adjust_brightness(img, brightness_value):
    """Adjust image brightness using OpenCV"""
    return cv2.convertScaleAbs(img, alpha=1.0, beta=-brightness_value)

def convert_to_ascii(img):
    """Convert OpenCV image to ASCII art"""
    print("Converting image to ASCII art...")
    # Convert BGR to RGB for PIL
    img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    
    # Save to temporary file since AsciiArt.from_image() requires a file path
    with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as tmp:
        tmp_path = tmp.name
        cv2.imwrite(tmp_path, img)
    
    try:
        my_art = AsciiArt.from_image(tmp_path)
        my_output = my_art.to_ascii(columns=200, monochrome=False)
    finally:
        # Clean up temporary file
        os.remove(tmp_path)
    
    return my_output

def save_ascii_art(my_output, filename):
    """Save ASCII art to file"""
    print(f"Saving ASCII art to {filename}...")
    with open(filename, 'w') as f:
        f.write(my_output)

def main():
    image_path = input("Enter image path: ").strip()
    
    # Expand home directory if needed
    image_path = os.path.expanduser(image_path)
    
    if not os.path.exists(image_path):
        print(f"Error: The file '{image_path}' does not exist.")
        return
    
    # Extract base name without extension for output files
    base_name = os.path.splitext(os.path.basename(image_path))[0]
    
    try:
        iterations = int(input("Enter number of iterations (brightness steps): "))
    except ValueError:
        print("Error: Please enter a valid number")
        return
    
    # Load image
    img = load_image(image_path)
    if img is None:
        return
    
    # Process image at different brightness levels
    brightness_step = 255 // iterations if iterations > 0 else 0
    
    for i in range(iterations):
        print(f"\n--- Processing iteration {i + 1}/{iterations} ---")
        
        # Adjust brightness
        brightness_value = brightness_step * i
        adjusted_img = adjust_brightness(img, brightness_value)
        
        # Convert to ASCII
        ascii_output = convert_to_ascii(adjusted_img)
        
        # Save to numbered file
        filename = f"{base_name}_{i + 1:03d}.txt"
        save_ascii_art(ascii_output, filename)
        print(f"Saved to {filename}")
    
    print("\nProcessing complete!")

if __name__ == "__main__":
    main()