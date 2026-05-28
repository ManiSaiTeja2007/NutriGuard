package com.google.mlkit.vision.text;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.common.InputImage;

public class TextRecognition {
    public static TextRecognizer getClient(TextRecognizerOptions options) {
        return new TextRecognizer() {
            @Override
            public Task<Text> process(InputImage image) {
                return Tasks.forResult(new Text());
            }
            @Override
            public void close() {}
        };
    }
}
