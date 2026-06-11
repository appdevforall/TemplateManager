package org.appdevforall.templatemanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import org.appdevforall.templatemanager.adapters.CgtFileAdapter
import org.appdevforall.templatemanager.databinding.ActivityMainBinding
import org.appdevforall.templatemanager.models.CgtFileItem
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CgtFileAdapter
    private var fileList = mutableListOf<CgtFileItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()

        // Trigger folder selection instantly on startup instead of looking for file permissions
        openDirectoryPicker()
    }

    private fun setupRecyclerView() {
        adapter = CgtFileAdapter(fileList)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnLoad.setOnClickListener {
            val selectedUris = adapter.getSelectedUris()
            handleLoadedFiles(selectedUris)
        }
    }

    private fun openDirectoryPicker() {
        // Launches system picker prompting user to select a directory to read
        dirPickerLauncher.launch(null)
    }

    private val dirPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take persistent read permissions over the directory structure
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            loadCgtFilesFromTree(uri)
        } else {
            Toast.makeText(this, "Directory access was canceled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCgtFilesFromTree(treeUri: Uri) {
        fileList.clear()

        val directory = DocumentFile.fromTreeUri(this, treeUri)
        val files = directory?.listFiles()

        files?.forEach { file ->
            // Check that it's a file type and matches the extension criteria
            if (file.isFile && file.name?.endsWith(".cgt", ignoreCase = true) == true) {
                fileList.add(
                    CgtFileItem(
                        uri = file.uri,
                        name = file.name ?: "Unknown File"
                    )
                )
            }
        }

        adapter.notifyDataSetChanged()

        if (fileList.isEmpty()) {
            Toast.makeText(this, "No .cgt files found in selected directory", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLoadedFiles(selectedUris: List<Uri>) {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
            return
        }

        // Display selection list
        Toast.makeText(this, "Loaded ${selectedUris.size} files safely!", Toast.LENGTH_LONG).show()

        // If you need to actually read text/bytes from one of these selected files, do this:
        // contentResolver.openInputStream(selectedUris[0]).use { stream -> ... }
    }

    /**
     * Opens a .cgt file wrapper as a ZIP container and extracts all internal files.
     * Returns a map of inner filenames to their raw uncompressed bytes.
     */
    private fun readCgtAsZipArchive(fileUri: Uri): Map<String, ByteArray> {
        val decompressedContents = mutableMapOf<String, ByteArray>()

        try {
            // Open the secure Uri stream directly into a buffered zip tracker
            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipStream ->
                    var entry: ZipEntry? = zipStream.nextEntry

                    while (entry != null) {
                        // Ignore directory entries, focus on compressed data payloads
                        if (!entry.isDirectory) {
                            val outputBuffer = ByteArrayOutputStream()
                            val buffer = ByteArray(1024)
                            var bytesRead = zipStream.read(buffer)

                            while (bytesRead != -1) {
                                outputBuffer.write(buffer, 0, bytesRead)
                                bytesRead = zipStream.read(buffer)
                            }

                            // Store the inner file name and its extracted data bytes
                            decompressedContents[entry.name] = outputBuffer.toByteArray()
                            zipStream.closeEntry()
                        }
                        entry = zipStream.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return decompressedContents
    }

}

