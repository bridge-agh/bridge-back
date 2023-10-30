package agh.bridge.firebase

import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.auth.oauth2.GoogleCredentials

object Authentication {
  private val serviceAccount = getClass.getResourceAsStream("/serviceAccountKey.json")

  private val options = new FirebaseOptions.Builder()
    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
    .build();

  private val app = FirebaseApp.initializeApp(options);
  private val auth = FirebaseAuth.getInstance(app);

  def verifyIdToken(idToken: String): Option[String] = {
    try {
      val decodedToken = auth.verifyIdToken(idToken)
      Some(decodedToken.getUid)
    } catch {
      case e: Exception => None
    }
  }
}
