package com.stampysoft.photoGallery.faces;

import com.stampysoft.util.Configuration;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN;
import org.bytedeco.opencv.opencv_objdetect.FaceRecognizerSF;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the two OpenCV models that turn an image into a list of faces with embeddings: YuNet for detection and
 * SFace for recognition.
 * <p>
 * <b>Neither underlying model is thread-safe</b>, so each worker thread needs its own FaceEncoder. Sharing one
 * across a pool produces garbage embeddings or segfaults. The {@link Mat}s here are native memory rather than heap,
 * so they're all closed explicitly; leaking them OOMs the JVM outside the heap, which is miserable to diagnose.
 * <p>
 * Detection and embedding are deliberately one stage: {@link #MODEL_VERSION} is recorded on every face, so
 * swapping in better embeddings later means re-running the backfill and nothing else.
 */
public class FaceEncoder implements AutoCloseable
{
    public static final int EMBEDDING_LENGTH = 128;

    public static final String YUNET_MODEL_FILENAME = "face_detection_yunet_2023mar.onnx";
    public static final String SFACE_MODEL_FILENAME = "face_recognition_sface_2021dec.onnx";

    /** Recorded on every face so that a future model swap only requires re-running detection. */
    public static final String MODEL_VERSION = "yunet-2023mar+sface-2021dec";

    /**
     * OpenCV's documented same-identity cutoff for SFace cosine similarity. Anything below this is a different
     * person as far as the model is concerned.
     */
    public static final float SAME_IDENTITY_COSINE = 0.363f;

    private static final String MODELS_DIRECTORY_PROPERTY = "FaceModelsDirectory";

    /** YuNet's detection confidence floor. Lower finds more faces in group shots at the cost of more junk. */
    private static final float SCORE_THRESHOLD = 0.7f;
    private static final float NMS_THRESHOLD = 0.3f;
    private static final int TOP_K = 5000;

    private final FaceDetectorYN _detector;
    private final FaceRecognizerSF _recognizer;

    public FaceEncoder()
    {
        File modelsDirectory = getModelsDirectory();
        if (modelsDirectory == null)
        {
            throw new FaceConfigurationException("No " + MODELS_DIRECTORY_PROPERTY + " is set in config.properties.");
        }
        File yunet = new File(modelsDirectory, YUNET_MODEL_FILENAME);
        File sface = new File(modelsDirectory, SFACE_MODEL_FILENAME);
        if (!yunet.isFile() || !sface.isFile())
        {
            throw new FaceConfigurationException("Missing face model files. Expected " + YUNET_MODEL_FILENAME +
                    " and " + SFACE_MODEL_FILENAME + " in " + modelsDirectory.getAbsolutePath() +
                    ", downloadable from https://github.com/opencv/opencv_zoo");
        }

        // The input size here is a placeholder: setInputSize() has to be called for each image anyway.
        // The trailing zeroes are backend_id and target_id, which mean "let OpenCV choose".
        _detector = FaceDetectorYN.create(yunet.getAbsolutePath(), "", new Size(320, 320),
                SCORE_THRESHOLD, NMS_THRESHOLD, TOP_K, 0, 0);
        _recognizer = FaceRecognizerSF.create(sface.getAbsolutePath(), "");
    }

    /** The directory holding the ONNX models, or null if it isn't configured. */
    public static File getModelsDirectory()
    {
        String value = Configuration.getConfiguration().getProperty(MODELS_DIRECTORY_PROPERTY, null);
        return value == null || value.isBlank() ? null : new File(value.trim());
    }

    /**
     * A description of what's missing before faces can be detected, or null if everything's in place. Lets the UI
     * explain itself instead of throwing when the optional configuration hasn't been filled in.
     */
    public static String describeMissingConfiguration()
    {
        File directory = getModelsDirectory();
        if (directory == null)
        {
            return "Set " + MODELS_DIRECTORY_PROPERTY + " in src/config.properties to the directory holding " +
                    YUNET_MODEL_FILENAME + " and " + SFACE_MODEL_FILENAME + ".";
        }
        if (!directory.isDirectory())
        {
            return MODELS_DIRECTORY_PROPERTY + " points at " + directory.getAbsolutePath() + ", which isn't a directory.";
        }
        List<String> missing = new ArrayList<>();
        for (String filename : new String[]{YUNET_MODEL_FILENAME, SFACE_MODEL_FILENAME})
        {
            if (!new File(directory, filename).isFile())
            {
                missing.add(filename);
            }
        }
        if (!missing.isEmpty())
        {
            return "Missing " + String.join(" and ", missing) + " in " + directory.getAbsolutePath() +
                    ". Download them from https://github.com/opencv/opencv_zoo";
        }
        return null;
    }

    public static boolean isConfigured()
    {
        return describeMissingConfiguration() == null;
    }

    /**
     * Detects every face in the given image and returns each one's normalized bounding box and L2-normalized
     * embedding. Returns an empty list when the image contains no faces, which is a normal outcome rather than
     * an error.
     */
    public List<DetectedFace> encode(File imageFile)
    {
        List<DetectedFace> result = new ArrayList<>();
        try (Mat image = opencv_imgcodecs.imread(imageFile.getAbsolutePath()))
        {
            if (image.empty())
            {
                throw new FaceDetectionException("Unable to read " + imageFile.getAbsolutePath());
            }

            int imageWidth = image.cols();
            int imageHeight = image.rows();

            try (Mat detections = new Mat())
            {
                // Required before every detect() on a differently-sized image, and every photo is a different size.
                _detector.setInputSize(new Size(imageWidth, imageHeight));
                _detector.detect(image, detections);

                if (detections.empty() || detections.rows() == 0)
                {
                    return result;
                }

                // Each row is [x, y, w, h, 5 landmark xy pairs, score] in CV_32F.
                try (FloatIndexer indexer = detections.createIndexer())
                {
                    for (int row = 0; row < detections.rows(); row++)
                    {
                        float[] embedding = extractEmbedding(image, detections, row);
                        if (embedding == null)
                        {
                            continue;
                        }
                        result.add(new DetectedFace(
                                indexer.get(row, 0) / imageWidth,
                                indexer.get(row, 1) / imageHeight,
                                indexer.get(row, 2) / imageWidth,
                                indexer.get(row, 3) / imageHeight,
                                indexer.get(row, detections.cols() - 1),
                                embedding));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Aligns and crops the one face in the given detection row, then runs it through SFace. Returns null if the
     * feature came back the wrong shape, which shouldn't happen but isn't worth aborting a whole scan over.
     */
    private float[] extractEmbedding(Mat image, Mat detections, int row)
    {
        try (Mat faceBox = detections.row(row);
             Mat aligned = new Mat();
             Mat feature = new Mat())
        {
            _recognizer.alignCrop(image, faceBox, aligned);
            _recognizer.feature(aligned, feature);

            int length = feature.rows() * feature.cols();
            if (length != EMBEDDING_LENGTH)
            {
                return null;
            }

            float[] embedding = new float[EMBEDDING_LENGTH];
            try (FloatIndexer indexer = feature.createIndexer())
            {
                for (int i = 0; i < EMBEDDING_LENGTH; i++)
                {
                    embedding[i] = indexer.get(0, i);
                }
            }
            return normalize(embedding);
        }
    }

    /**
     * Scales the vector to unit length so that cosine similarity and inner product agree, which is what lets the
     * pgvector cosine index and any hand-rolled dot product give the same answer.
     */
    static float[] normalize(float[] vector)
    {
        double sumOfSquares = 0;
        for (float value : vector)
        {
            sumOfSquares += (double) value * value;
        }
        if (sumOfSquares == 0)
        {
            return vector;
        }
        float length = (float) Math.sqrt(sumOfSquares);
        for (int i = 0; i < vector.length; i++)
        {
            vector[i] = vector[i] / length;
        }
        return vector;
    }

    /** Cosine similarity of two embeddings that are already unit length. */
    public static float similarity(float[] a, float[] b)
    {
        float total = 0;
        for (int i = 0; i < a.length && i < b.length; i++)
        {
            total += a[i] * b[i];
        }
        return total;
    }

    @Override
    public void close()
    {
        _detector.close();
        _recognizer.close();
    }

    /** One detected face: bounding box normalized 0..1 against the image, plus its unit-length embedding. */
    public record DetectedFace(float x, float y, float w, float h, float detectScore, float[] embedding)
    {
    }

    /** Thrown when the models aren't configured or aren't where they're supposed to be. */
    public static class FaceConfigurationException extends RuntimeException
    {
        public FaceConfigurationException(String message)
        {
            super(message);
        }
    }

    /** Thrown when a specific image can't be processed, leaving the rest of a scan to continue. */
    public static class FaceDetectionException extends RuntimeException
    {
        public FaceDetectionException(String message)
        {
            super(message);
        }
    }
}
