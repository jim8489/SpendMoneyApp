package com.java.moneyspendapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;

    private EditText dateInput, spendAmountInput, causeInput;
    private Button saveButton, fetchDataButton;
    private static final int PERMISSION_REQUEST_STORAGE = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDatabase = FirebaseDatabase.getInstance().getReference();

        dateInput = findViewById(R.id.date_Input);
        spendAmountInput = findViewById(R.id.spend_AmountInput);
        causeInput = findViewById(R.id.cause_Input);
        saveButton = findViewById(R.id.save_Button);
        fetchDataButton = findViewById(R.id.fetch_DataButton);
        // Set up OnClickListener for the dateInput EditText
        dateInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveData();
            }
        });

        fetchDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fetchDataAndCreatePDF();
                checkStoragePermission();
            }
        });
    }

    private void showDatePicker() {
        // Get current date
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);

        // Show DatePickerDialog
        DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        // Update dateInput EditText with selected date
                        Calendar selectedDate = Calendar.getInstance();
                        selectedDate.set(Calendar.YEAR, year);
                        selectedDate.set(Calendar.MONTH, monthOfYear);
                        selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        String formattedDate = dateFormat.format(selectedDate.getTime());
                        dateInput.setText(formattedDate);
                    }
                }, year, month, dayOfMonth);

        datePickerDialog.show();
    }

    private void saveData() {
        String dateString = dateInput.getText().toString().trim();
        String amountString = spendAmountInput.getText().toString().trim();
        String cause = causeInput.getText().toString().trim();

        if (!dateString.isEmpty() && !amountString.isEmpty() && !cause.isEmpty()) {
            try {
                int amount = Integer.parseInt(amountString); // Convert amount to integer
                // Format the date as a Firebase-safe string (e.g., "ddMMyyyy")
                SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyyyy", Locale.getDefault());
                Date date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateString);
                String firebaseDateKey = dateFormat.format(date);

                SpendEntry entry = new SpendEntry(dateString, String.valueOf(amount), cause);

                // Here we use the date as a key directly. If you expect multiple entries per date, consider using a child structure.
                mDatabase.child("spends").child(firebaseDateKey).setValue(entry)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Toast.makeText(MainActivity.this, "Data saved successfully", Toast.LENGTH_SHORT).show();
                                // Clear the input fields after successful save
                                spendAmountInput.setText("");
                                causeInput.setText("");
                                dateInput.setText("");
                                spendAmountInput.requestFocus();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(MainActivity.this, "Failed to save data", Toast.LENGTH_SHORT).show();
                            }
                        });
            } catch (NumberFormatException | ParseException e) {
                Toast.makeText(MainActivity.this, "Please ensure all fields are correctly filled. Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(MainActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
        }
    }


    private void fetchDataAndCreatePDF() {
        mDatabase.child("spends").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                StringBuilder dataBuilder = new StringBuilder();
                int totalAmount = 0; // Initialize total amount
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    SpendEntry entry = snapshot.getValue(SpendEntry.class);
                    dataBuilder.append("Date: ").append(entry.date)
                            .append(", Amount: ").append(entry.amount)
                            .append(", Cause: ").append(entry.cause).append("\n");
                    totalAmount += Integer.parseInt(entry.amount); // Calculate total amount
                }
                // Now create a PDF with this data and include the total amount
                createPdf(dataBuilder.toString(), totalAmount);

                // Open the PDF automatically
                openPdf();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.out.println("The read failed: " + databaseError.getCode());
            }
        });
    }



    // Method to create PDF
    private void createPdf(String data, int totalAmount) {
        // Check if external storage is available
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Toast.makeText(MainActivity.this, "External storage is not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use the public Documents directory for saving the PDF
        File pdfFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MyAppPDFs");
        if (!pdfFolder.exists()) {
            if (!pdfFolder.mkdirs()) {
                Toast.makeText(MainActivity.this, "Failed to create directory to save PDF", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Create a file name
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fileName = "spend_report_" + dateFormat.format(new Date()) + ".pdf";
        File myFile = new File(pdfFolder, fileName);

        try {
            FileOutputStream outputStream = new FileOutputStream(myFile);
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);

            // Adding a bold header at the top of the PDF
            Paragraph header = new Paragraph("Here is the final spend money").setBold();
            document.add(header);

            // Adding the data
            document.add(new Paragraph(data));

            // Adding the sum of the total amount calculated at the bottom, bold
            Paragraph total = new Paragraph("Total Amount: " + totalAmount).setBold();
            document.add(total);

            document.close();
            outputStream.close();

            Toast.makeText(this, "PDF saved to Documents", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Failed to create PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openPdf() {
        // Get the file path of the saved PDF
        File pdfFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MyAppPDFs");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fileName = "spend_report_" + dateFormat.format(new Date()) + ".pdf";
        File pdfFile = new File(pdfFolder, fileName);

        // Check if the PDF file exists
        if (!pdfFile.exists()) {
            Toast.makeText(this, "PDF file does not exist", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a URI for the PDF file using FileProvider
        Uri pdfUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", pdfFile);

        // Create an intent to open the PDF
        Intent pdfIntent = new Intent(Intent.ACTION_VIEW);
        pdfIntent.setDataAndType(pdfUri, "application/pdf");
        pdfIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        pdfIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        // Check if there's an app to handle the PDF intent
        if (pdfIntent.resolveActivity(getPackageManager()) != null) {
            // Launch the PDF viewer app
            startActivity(pdfIntent);
        } else {
            // Handle the case where no app is available to handle the PDF intent
            Toast.makeText(this, "No application available to view PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted, request it
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_STORAGE);
            } else {
                // Permission already granted, proceed with the action that requires this permission
                fetchDataAndCreatePDF();
            }
        } else {
            // For devices below Marshmallow, permission is granted at installation time
            fetchDataAndCreatePDF();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with the action that requires this permission
                fetchDataAndCreatePDF();
            } else {
                // Permission denied
                Toast.makeText(this, "Storage permission is required to create PDF", Toast.LENGTH_SHORT).show();
            }
        }
    }


}
