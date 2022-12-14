package app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import app.common.Utils;
import app.common.debug.DebugUtils;
import app.App;
import app.internal.Preferences;
import app.Res;
import app.UpdaterService;
import app.info.Statistics;
import app.internal.Settings;
import app.security.Database;
import app.security.Policy;
import java.io.File;
import org.squareup.okhttp.OkHttpClient;
import org.squareup.okhttp.OkUrlFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


public class SendMessageActivity extends Activity implements Runnable
{
	String textToSend = null;
	String mailToSend = null;
	String publisher = null;
	String referrer = null;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(android.R.style.Theme_DeviceDefault_Light);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(Res.layout.send_message_activity);
		getActionBar().setDisplayHomeAsUpEnabled(true);

		// hide progressbar manually (Samsung devices bug)
		setProgressBarIndeterminateVisibility(false);
		overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);

		((EditText) findViewById(Res.id.message)).requestFocus();

		publisher = Preferences.getPublisher();
		referrer = Preferences.getReferrer(true);
		if (referrer == null)
			referrer = "";

		Intent intent = getIntent();

		if (intent.hasExtra("text"))
		{
			final String text = intent.getStringExtra("text");
			if (text != null)
				((EditText) findViewById(Res.id.message)).setText(text);
		}
	}

	public static void sendMessage(String message, boolean isBlockError)
	{
		Context context = App.getContext();
		Intent intent = new Intent(context, SendMessageActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		String text = null;
		if (isBlockError)
		{
			text = context.getString(Res.string.blocked_error);
			if (message != null)
				text = text.replace("%s", message);
		}
		else
		{
			text = message;
		}
		intent.putExtra("text", text);

		context.startActivity(intent);
	}

	//

	public void sendMessageClick(View view)
	{
		EditText edit = (EditText) findViewById(Res.id.message);
		EditText mail = (EditText) findViewById(Res.id.mail);
		Button send = (Button) findViewById(Res.id.send_button);

		String message = edit.getText().toString().trim();
		String mailAddr = mail.getText().toString().trim();

		if (message.length() > 2)
		{
			textToSend = message + "\r\n\r\n" + Preferences.getDeviceInfo();
			mailToSend = mailAddr;

			setProgressBarIndeterminate(true);
			setProgressBarIndeterminateVisibility(true);

			send.setEnabled(false);
			edit.setEnabled(false);
			mail.setEnabled(false);

			new Thread(this).start();
		}
	}

	private void sendResult(boolean ok)
	{
		setProgressBarIndeterminateVisibility(false);

		EditText edit = (EditText) findViewById(Res.id.message);
		EditText mail = (EditText) findViewById(Res.id.mail);
		Button send = (Button) findViewById(Res.id.send_button);

		send.setEnabled(true);
		edit.setEnabled(true);
		mail.setEnabled(true);

		showDialog(ok, (mailToSend == null || mailToSend.isEmpty()));
	}

	// TODO XXX move this code to dialogs
	private void showDialog(final boolean ok, final boolean noEmail)
	{
		AlertDialog.Builder alertbox = new AlertDialog.Builder(this);
		alertbox.setTitle(getString(Res.string.message_to_developer));

		int statusTextResource =
			(!ok) ? Res.string.message_undelivered : ((noEmail) ? Res.string.message_noemail : Res.string.message_sent);
			//(noEmail) ? Res.string.message_noemail : ((ok) ? Res.string.message_sent : Res.string.message_undelivered);
		alertbox.setMessage(statusTextResource);
		alertbox.setNeutralButton(getString(android.R.string.ok), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int which)
			{
				Utils.dialogClose(null, (Dialog) dialog, false);
			}
		});

		Dialog dialog = alertbox.create();
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
		{
			@Override
			public void onDismiss(DialogInterface dialog)
			{
				if (ok && !noEmail)
					SendMessageActivity.this.finish();
			}
		});

		Utils.dialogShow(this, dialog);
	}

	@Override
	public void run()
	{
		boolean status = false;
		HttpURLConnection con = null;

		try
		{
			String addr = Preferences.get_s(Settings.PREF_UPDATE_URL);
			if (addr == null)
				return;

			// TODO XXX rewrite this with normal framework

			String bases = Database.getCurrentVersion();
			addr += "?action=message&version=" + bases	+ "&appversion=" + Statistics.versionCode;
			final String token = Policy.getUserToken(false);
			if (token != null)
				addr += "&token=" + token;

			//String params = "publisher=" + publisher + referrer + "&mail=" + URLEncoder.encode(mailToSend, "UTF-8") +
			//					  "&text=" + URLEncoder.encode(textToSend, "UTF-8");

			// get additional debug info
			final String debugFile = DebugUtils.disableDebugInfoCollect(true);

			byte[] debugData = null;
			try { debugData = Utils.getFileContentsRaw(debugFile); } catch (IOException e) { }
			if (debugData != null)
				debugData = Utils.deflate(debugData);

			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url);

			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			con.setConnectTimeout(15000); // TODO XXX 15 sec, maybe low?
			con.setReadTimeout((debugData != null) ? 300000 : 15000);

			// see http://ru.wikipedia.org/wiki/Multipart/form-data

			final String crlf = "\r\n";
			final String twoHyphens = "--";
			final String boundary =  "*******";
			final String replacement =	"_______";
			final byte[] boundaryFull = (twoHyphens + boundary + crlf).getBytes();

			//con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			con.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

			try { con.connect(); }
			catch (SecurityException ex) { return; }

			OutputStream out = con.getOutputStream();
			//out.write(params.getBytes());

			out.write(boundaryFull);
			out.write(("Content-Disposition: form-data; name=\"publisher\"" + crlf + crlf).getBytes());
			out.write((publisher).replace((CharSequence) boundary, (CharSequence) replacement).getBytes());
			out.write(crlf.getBytes());

			out.write(boundaryFull);
			out.write(("Content-Disposition: form-data; name=\"referrer\"" + crlf + crlf).getBytes());
			out.write((referrer).replace((CharSequence) boundary, (CharSequence) replacement).getBytes());
			out.write(crlf.getBytes());

			out.write(boundaryFull);
			out.write(("Content-Disposition: form-data; name=\"mail\"" + crlf + crlf).getBytes());
			out.write(mailToSend.replace((CharSequence) boundary, (CharSequence) replacement).getBytes());
			out.write(crlf.getBytes());

			out.write(boundaryFull);
			out.write(("Content-Disposition: form-data; name=\"text\"" + crlf + crlf).getBytes());
			out.write(textToSend.replace((CharSequence) boundary, (CharSequence) replacement).getBytes());
			out.write(crlf.getBytes());

			if (debugData != null)
			{
				// send debug data

				out.write(boundaryFull);
				//out.write(("Content-Disposition: form-data; name=\"debug\";filename=\"debug.bin\"" + crlf + crlf).getBytes());
				out.write(("Content-Disposition: form-data; name=\"debug\"" + crlf + crlf).getBytes());
				out.write(debugData);
				out.write(crlf.getBytes());
			}

			out.write((twoHyphens + boundary + twoHyphens + crlf).getBytes());
			out.flush();
			out.close();

			int code = con.getResponseCode();
			status = (code == 200);
			if (status)
			{
				// delete debug data
				File tmp = new File(debugFile);
				tmp.delete();
			}
		}

		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e)
		{
			e.printStackTrace();
			status = false;
		}

		finally
		{
			if (con != null)
				con.disconnect();
		}

		//

		if (status) // start force update also (send all info and logs)
			UpdaterService.startUpdate(UpdaterService.START_FORCE); // message to devs

		final boolean s = status;
		SendMessageActivity.this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				sendResult(s);
			}
		});
	}

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();

		overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				onBackPressed();
				break;

			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}
}
