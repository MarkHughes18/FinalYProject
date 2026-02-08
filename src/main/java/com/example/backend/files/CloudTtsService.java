package com.example.backend.files;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;

@Service
public class CloudTtsService {

    public byte[] synthesizeMp3(String text, String lang, String voice)
            throws Exception {
        String resolvedLang = (lang == null || lang.isBlank()) ? "en-GB" : lang.trim();

        String resolvedVoice = (voice == null || voice.isBlank()) ? "female" : voice.trim();
        String voiceName = null;
        SsmlVoiceGender gender = SsmlVoiceGender.NEUTRAL;

        String vLower = resolvedVoice.toLowerCase();
        if (vLower.contains("-")) {
            voiceName = resolvedVoice;
        } else if (vLower.equals("female") || vLower.equals("woman")) {
            gender = SsmlVoiceGender.FEMALE;
        } else if (vLower.equals("male") || vLower.equals("man")) {
            gender = SsmlVoiceGender.MALE;
        } else {
            gender = SsmlVoiceGender.NEUTRAL;
        }
        try (TextToSpeechClient client = TextToSpeechClient.create()) {
            SynthesisInput input = SynthesisInput.newBuilder()
                    .setText(text == null ? "" : text)
                    .build();

            VoiceSelectionParams.Builder voiceBuilder = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(resolvedLang)
                    .setSsmlGender(gender);

            if (voiceName != null) {
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
        return synthesizeMp3(text, "en-GB", "female");
    }
}