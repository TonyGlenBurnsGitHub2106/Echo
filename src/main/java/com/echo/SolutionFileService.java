package com.echo;

import com.echo.SolutionModels.FileRow;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

/**
 * Files attached to a solution (Story 3493), Echo's way (Story 5653).
 *
 * LeonardoWeb seals every solution file as a secure Leonardo File. Echo has no Leonardo Files, so
 * the bytes live in Echo's own files folder ({@code echo.files.path}, default {@code EchoFiles}
 * beside the running jar -- never inside the git repository), one sub-folder per solution:
 *
 *   EchoFiles/<solutionId>/<8 random characters>-<safe name>     StorageCode 'local'
 *
 * Everything else is LeonardoWeb's rule, kept: what an agent uploads is hostile until proven
 * otherwise, so the stored name is rebuilt from a safe alphabet (no path, no leading dot), every
 * resolved path is checked to still sit inside the files folder, and downloads are always served
 * as attachments (see the controller). Detaching never unlinks bytes: the file is MOVED to
 * {@code EchoFiles/trash}, where it can still be found.
 */
@Service
public class SolutionFileService {

    private static final Logger log = LoggerFactory.getLogger(SolutionFileService.class);

    static final String LOCAL = "local";
    static final long MAX_BYTES = 50L * 1024 * 1024;

    private final SolutionFileRepository files;
    private final SolutionAccess         access;
    private final Path filesDir;
    private final Path trashDir;

    public SolutionFileService(SolutionFileRepository files, SolutionAccess access,
                               @Value("${echo.files.path:EchoFiles}") String filesPath) {
        this.files = files; this.access = access;
        this.filesDir = Path.of(filesPath).toAbsolutePath().normalize();
        this.trashDir = filesDir.resolve("trash");
    }

    @PostConstruct
    void ensureDirs() throws IOException { Files.createDirectories(filesDir); }

    public List<FileRow> list(SolutionCaller caller, int solutionId, Integer itemId) {
        access.requireSolution(caller, solutionId);
        return files.listForSolution(solutionId, itemId);
    }

    public FileRow get(SolutionCaller caller, int fileId) {
        FileRow f = fileId <= 0 ? null : files.find(fileId);
        if (f == null) throw new SolutionNotFoundException("File");
        access.requireSolution(caller, f.solutionId());
        return f;
    }

    public FileRow upload(SolutionCaller caller, int solutionId, Integer itemId, String title, String notes,
                          String originalName, String contentType, long sizeBytes, InputStream content) throws IOException {
        access.requireSolution(caller, solutionId);
        if (sizeBytes <= 0) throw new IllegalArgumentException("The file is empty.");
        if (sizeBytes > MAX_BYTES) throw new IllegalArgumentException("A file may be at most 50 MB.");
        String safeName = safeName(originalName);

        byte[] bytes = content.readNBytes((int) MAX_BYTES + 1);
        if (bytes.length == 0) throw new IllegalArgumentException("The file is empty.");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("A file may be at most 50 MB.");

        Path dir = filesDir.resolve(String.valueOf(solutionId));
        Files.createDirectories(dir);
        String stored = UUID.randomUUID().toString().substring(0, 8) + "-" + safeName;
        Path target = inside(dir.resolve(stored));
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        int id;
        try {
            id = files.insert(solutionId, itemId, title, safeName, LOCAL, null, solutionId + "/" + stored, contentType,
                              bytes.length, notes, caller.memberCode());
        } catch (RuntimeException ex) {
            moveToTrash(target);                       // a refused row must not leave an orphan behind
            throw ex;
        }
        return files.find(id);
    }

    public FileRow update(SolutionCaller caller, int fileId, Integer itemId, String title, String notes) {
        FileRow current = get(caller, fileId);
        Integer item = itemId == null ? current.itemId() : itemId;
        String newNotes = notes == null ? current.notes() : notes;
        if (!files.update(fileId, item, title, newNotes)) throw new SolutionNotFoundException("File");
        return files.find(fileId);
    }

    /** Where the bytes are, for a download. Always a path proven to be inside the files folder. */
    public Path pathOf(FileRow f) {
        if (f.storedFileName() == null) throw new SolutionNotFoundException("File");
        Path p = inside(filesDir.resolve(f.storedFileName()));
        if (!Files.isRegularFile(p)) throw new SolutionNotFoundException("File");
        return p;
    }

    /**
     * Opens Explorer at the file, when Echo runs in the signed-in person's own Windows session
     * (started by start-echo.cmd or the sign-in task). Run as a Windows service, Echo cannot show
     * a window; the answer then names the folder so it can be opened by hand. A person only.
     */
    public String openFolder(SolutionCaller caller, int fileId) {
        FileRow f = get(caller, fileId);                     // visibility first: a hidden file is "not found"
        access.requireHuman(caller, "Only a person may open a folder on this computer.");
        Path p = pathOf(f);
        try {
            new ProcessBuilder("explorer.exe", "/select,", p.toString()).start();
        } catch (IOException ex) {
            log.info("Solutions: could not open Explorer at {}: {}", p, ex.getMessage());
        }
        return p.getParent().toString();
    }

    public void delete(SolutionCaller caller, int fileId) throws IOException {
        FileRow f = get(caller, fileId);
        if (!files.delete(fileId)) throw new SolutionNotFoundException("File");
        if (f.storedFileName() != null) {
            Path p = inside(filesDir.resolve(f.storedFileName()));
            if (Files.isRegularFile(p)) moveToTrash(p);
        }
    }

    // ---- helpers ----------------------------------------------------------

    /** Refuses any path that has escaped the files folder, however it was built. */
    private Path inside(Path candidate) {
        Path p = candidate.toAbsolutePath().normalize();
        if (!p.startsWith(filesDir)) throw new SolutionNotFoundException("File");
        return p;
    }

    /** Soft delete: EchoFiles/trash, where the file can still be found. Never an unlink. */
    private void moveToTrash(Path file) throws IOException {
        Files.createDirectories(trashDir);
        Path target = trashDir.resolve(file.getFileName().toString());
        for (int n = 2; Files.exists(target); n++) target = trashDir.resolve(n + "-" + file.getFileName());
        Files.move(file, target, StandardCopyOption.ATOMIC_MOVE);
    }

    /** A file name rebuilt from a safe alphabet: no directories, no leading dot, no control or shell characters. */
    static String safeName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ ()-]", "_").trim();
        while (name.startsWith(".")) name = name.substring(1);
        if (name.isEmpty()) name = "file";
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
