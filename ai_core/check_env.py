import torch
import ultralytics

print("=== Verificación del entorno de IA ===")
print(f"Version de PyTorch: {torch.__version__}")
print(f"Version de Ultralytics (YOLO): {ultralytics.__version__}")

#Verificación de CUDA/ GPU NVIDIa
cuda_available = torch.cuda.is_available()
print(f"CUDA disponible: {cuda_available}")

if cuda_available:
    print(f"Dispositivo de GPU detectado: {torch.cuda.get_device_name(0)}")
    print(f"Número de GPUs disponibles: {torch.cuda.device_count()}")
    print(f"Memoria CUDA disponible: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.2f} GB")
else:
    print("No se encontró GPU NVIDIA. El modelo se ejecutará en CPU.")
