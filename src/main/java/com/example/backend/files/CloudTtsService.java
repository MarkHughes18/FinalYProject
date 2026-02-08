package com.example.backend.files;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;

@Service
public class CloudTtsService {

    public byte[] synthesizeMp3(String text, String languageCode, String voiceName, SsmlVoiceGender gender)
            throws Exception {
        try (TextToSpeechClient client = TextToSpeechClient.create()) {
            if (text == null)
                text = "";
            if (languageCode == null || languageCode.isBlank())
                languageCode = "en-GB";
            if (gender == null)
                gender = SsmlVoiceGender.NEUTRAL;

            SynthesisInput input = SynthesisInput.newBuilder()
                    .setText(text)
                    .build();

            VoiceSelectionParams.Builder voiceBuilder = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(languageCode)
                    .setSsmlGender(gender);

            // use a specific voice name
            if (voiceName != null && !voiceName.isBlank()) {
                voiceBuilder.setName(voiceName);
            }

            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build();

            SynthesizeSpeechResponse response = client.synthesizeSpeech(input, voiceBuilder.build(), audioConfig);
            ByteString audioBytes = response.getAudioContent();
            return audioBytes.toByteArray();
        }
    }

    public byte[] synthesizeMp3(String text) throws Exception {
        return synthesizeMp3(text, "en-GB", null, SsmlVoiceGender.NEUTRAL);
    }
}
