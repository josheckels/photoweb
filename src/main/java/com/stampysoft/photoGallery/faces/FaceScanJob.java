package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.Photo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 3: detect and embed every face in the photos that haven't been scanned yet.
 * <p>
 * Resumable by construction - {@code face_scanned_on} is stamped on every photo in a committed batch, whether or
 * not it turned out to contain anyone - so a crash or a cancel costs at most one batch. Parallelized across cores,
 * with a {@link FaceEncoder} per worker because the models aren't thread-safe.
 */
public class FaceScanJob implements FaceProgressDialog.Task
{
    /** Photos per transaction. Small enough that cancelling is quick, big enough that commits aren't the cost. */
    private static final int BATCH_SIZE = 200;

    private final FaceOperations _faceOperations;

    private final AtomicInteger _photosScanned = new AtomicInteger();
    private final AtomicInteger _facesFound = new AtomicInteger();
    private final List<String> _failures = Collections.synchronizedList(new ArrayList<>());

    public FaceScanJob(FaceOperations faceOperations)
    {
        _faceOperations = faceOperations;
    }

    public int getPhotosScanned()
    {
        return _photosScanned.get();
    }

    public int getFacesFound()
    {
        return _facesFound.get();
    }

    public List<String> getFailures()
    {
        return _failures;
    }

    @Override
    public void run(FaceMatcher.ProgressListener listener) throws Exception
    {
        List<Integer> photoIds = _faceOperations.getUnscannedPhotoIds();
        if (photoIds.isEmpty())
        {
            return;
        }

        List<List<Integer>> batches = new ArrayList<>();
        for (int start = 0; start < photoIds.size(); start += BATCH_SIZE)
        {
            batches.add(photoIds.subList(start, Math.min(start + BATCH_SIZE, photoIds.size())));
        }

        int workerCount = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() - 1, batches.size()));
        AtomicInteger nextBatch = new AtomicInteger();
        int total = photoIds.size();

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < workerCount; i++)
        {
            Thread worker = new Thread(() -> {
                // One encoder per thread. FaceDetectorYN and FaceRecognizerSF are not thread-safe, and sharing one
                // across a pool produces garbage embeddings or segfaults rather than an exception.
                try (FaceEncoder encoder = new FaceEncoder())
                {
                    int index;
                    while ((index = nextBatch.getAndIncrement()) < batches.size() && !listener.isCancelled())
                    {
                        scanBatch(encoder, batches.get(index));
                        listener.progress("Detecting faces", _photosScanned.get(), total);
                    }
                }
                catch (Throwable t)
                {
                    // Throwable rather than Exception on purpose: OpenCV's natives failing to load surfaces as
                    // UnsatisfiedLinkError and NoClassDefFoundError, and those would otherwise kill this worker
                    // silently and leave the job reporting that it scanned nothing at all for no stated reason.
                    _failures.add(t.getMessage() == null ? t.toString() : t.getMessage());
                }
            }, "Face scan " + (i + 1));
            worker.setPriority(Thread.MIN_PRIORITY);
            workers.add(worker);
            worker.start();
        }

        for (Thread worker : workers)
        {
            worker.join();
        }
        listener.progress("Detecting faces", _photosScanned.get(), total);
    }

    private void scanBatch(FaceEncoder encoder, List<Integer> photoIds)
    {
        Map<Integer, List<FaceEncoder.DetectedFace>> results = new LinkedHashMap<>();
        for (Photo photo : _faceOperations.getPhotosByIds(photoIds))
        {
            File file = FaceImages.getDetectionFile(photo);
            if (file == null || !file.isFile())
            {
                // Nothing readable on disk. Stamp it anyway so the backfill doesn't keep coming back to it.
                results.put(photo.getPhotoId(), List.of());
                _failures.add("No image on disk for " + photo.getFilename());
                continue;
            }
            try
            {
                List<FaceEncoder.DetectedFace> faces = encoder.encode(file);
                results.put(photo.getPhotoId(), faces);
                _facesFound.addAndGet(faces.size());
            }
            catch (RuntimeException | LinkageError e)
            {
                results.put(photo.getPhotoId(), List.of());
                _failures.add(photo.getFilename() + ": " + e.getMessage());
            }
        }

        _faceOperations.saveScanResults(results);
        _photosScanned.addAndGet(photoIds.size());
    }
}
