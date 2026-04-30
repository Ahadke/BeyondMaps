# Push the Gemma 4 model to the physical phone
adb -d push ~/Downloads/gemma-4-E2B-it_qualcomm_sm8750.litertlm /sdcard/Android/data/com.example.qnn_litertlm_gemma/files/model.litertlm

# Push the FastVLM model to the physical phone
adb -d push ~/Downloads/FastVLM-0.5B.qualcomm.sm8750.litertlm /sdcard/Android/data/com.example.qnn_litertlm_gemma/files/FastVLM-0.5B.qualcomm.sm8750.litertlm