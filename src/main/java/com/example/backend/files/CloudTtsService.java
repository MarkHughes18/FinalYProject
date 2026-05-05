package com.example.backend.files;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;

@Service
public class CloudTtsService {
    // converts text into mp3 audio using google cloud text to speech api

    public byte[] synthesizeMp3(String text, String lang, String voice)
            throws Exception {

        // deafult british voice
        String resolvedLang = (lang == null || lang.isBlank()) ? "en-GB" : lang.trim();

        // default to feamle voice
        String resolvedVoice = (voice == null || voice.isBlank()) ? "female" : voice.trim();
        String voiceName = null;
        SsmlVoiceGender gender = SsmlVoiceGender.NEUTRAL;

        // choose voice
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

        // create google TTS client and synthesize speech
        try (TextToSpeechClient client = TextToSpeechClient.create()) {
            SynthesisInput input = SynthesisInput.newBuilder()
                    .setText(text == null ? "" : text)
                    .build();

            // build voice selection params
            VoiceSelectionParams.Builder voiceBuilder = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(resolvedLang)
                    .setSsmlGender(gender);

            if (voiceName != null) {
                voiceBuilder.setName(voiceName);
            }

            // request mp3 output
            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build();

            // send the request and return the audio bytes
            SynthesizeSpeechResponse response = client.synthesizeSpeech(input, voiceBuilder.build(), audioConfig);
            ByteString audioBytes = response.getAudioContent();
            return audioBytes.toByteArray();
        }
    }

    // fall back with defaults
    public byte[] synthesizeMp3(String text) throws Exception {
        return synthesizeMp3(text, "en-GB", "female");
    }
}