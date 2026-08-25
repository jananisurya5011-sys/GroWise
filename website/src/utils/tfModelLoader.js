import * as tf from '@tensorflow/tfjs';
import * as tflite from '@tensorflow/tfjs-tflite';

class TFModelLoader {
  constructor() {
    this.model = null;
    this.labels = [];
    this.isInitializing = false;
  }

  async initialize() {
    if (this.model) return;
    if (this.isInitializing) {
      // Wait for the initialization to finish
      while (this.isInitializing) {
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
      return;
    }

    this.isInitializing = true;
    try {
      // 1. Force WASM backend location to be local for 100% offline capability
      tflite.setWasmPath('/tflite/');
      
      // 2. Load the actual crop_model.tflite directly using WASM
      this.model = await tflite.loadTFLiteModel('/ml/crop_model.tflite');
      
      // 3. Load labels dynamically
      const response = await fetch('/ml/labels.txt');
      const text = await response.text();
      this.labels = text.split('\n').map(l => l.trim()).filter(l => l.length > 0);
      
      console.log('Offline TFLite Crop Model & Labels Loaded Successfully');
    } catch (err) {
      console.error('Failed to load TFLite model or labels:', err);
    } finally {
      this.isInitializing = false;
    }
  }

  async predict(imageElement) {
    if (!this.model || this.labels.length === 0) await this.initialize();
    if (!this.model) throw new Error("Model failed to load");

    return tf.tidy(() => {
      // 1. Convert HTML image to tensor
      let tensor = tf.browser.fromPixels(imageElement);
      
      // 2. Resize to 224x224
      tensor = tf.image.resizeBilinear(tensor, [224, 224]);
      
      // 3. Normalize to [0, 1] float, matching Keras model expectations.
      tensor = tf.cast(tensor, 'float32').div(255.0);
      
      // 4. Add batch dimension: shape becomes [1, 224, 224, 3]
      const batched = tensor.expandDims(0);
      
      console.log('Model Successfully Loaded:', this.model !== null);
      console.log('Labels Count:', this.labels.length);
      console.log('Input Tensor Shape:', batched.shape);
      
      // 5. Predict using TFLite WASM engine
      const prediction = this.model.predict(batched);
      
      console.log('Output Tensor Shape:', prediction.shape);
      
      // 6. Extract probabilities (dataSync downloads tensor to CPU array)
      const probabilities = prediction.dataSync();
      
      console.log('Raw Output Probabilities:', probabilities);
      
      // 7. Find highest probability
      let maxIndex = 0;
      let maxConfidence = probabilities[0];
      for (let i = 1; i < probabilities.length; i++) {
        if (probabilities[i] > maxConfidence) {
          maxConfidence = probabilities[i];
          maxIndex = i;
        }
      }
      
      const predictedLabel = this.labels[maxIndex] || "Unknown";
      
      console.log('Predicted Index:', maxIndex);
      console.log('Predicted Label:', predictedLabel);
      
      return {
        disease: predictedLabel,
        confidence: maxConfidence
      };
    });
  }
}

// Singleton instance
const tfModelLoader = new TFModelLoader();
export default tfModelLoader;
