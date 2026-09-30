# Application Android CESMAN

Application qui affiche https://www.cesmaneg.com/ en plein écran, comme une vraie app.
Tout contenu modifié sur Wix apparaît aussitôt dans l'application : rien à republier.

## Ce que fait l'application
- Écran de démarrage, icône et nom « CESMAN »
- Bouton Retour du téléphone = page précédente du site
- Tirer vers le bas pour actualiser
- Écran « Pas de connexion » avec bouton Réessayer
- Liens téléphone, WhatsApp, e-mail, Google Maps, Zoom… ouverts dans l'application adaptée
- Envoi de fichiers (formulaires, pièces jointes) et accès caméra/micro si le site en demande
- Connexion membre conservée entre deux ouvertures

## Obtenir l'APK — méthode 1 : Android Studio (sur PC)
1. Installer Android Studio (gratuit) : https://developer.android.com/studio
2. Décompresser ce dossier, puis Fichier › Ouvrir › choisir le dossier `CESMAN-Android`.
3. Attendre la fin de la synchronisation Gradle (la 1re fois, quelques minutes).
4. Menu Build › Build App Bundle(s) / APK(s) › Build APK(s).
5. Le fichier est dans `app/build/outputs/apk/debug/app-debug.apk` : l'envoyer sur un
   téléphone (WhatsApp, câble…) et l'installer (autoriser « sources inconnues »).

## Obtenir l'APK — méthode 2 : GitHub, sans rien installer
1. Créer un dépôt sur github.com et y téléverser tout le contenu du dossier.
2. Onglet Actions : le travail « Construire l'APK » se lance tout seul.
3. Après ~5 min, ouvrir l'exécution terminée et télécharger « CESMAN-apk ».

## Publier sur Google Play
1. Compte développeur Google Play : 25 $ une seule fois.
2. Android Studio › Build › Generate Signed App Bundle / APK › Android App Bundle,
   créer une clé de signature (la conserver précieusement, elle est définitive).
3. Téléverser le fichier `.aab` dans la Play Console, avec captures d'écran,
   description et politique de confidentialité (le site en a besoin, vu les données de santé).
4. À chaque nouvelle version : augmenter `versionCode` dans `app/build.gradle`.

## Personnaliser
- Icône : remplacer les fichiers `app/src/main/res/mipmap-*/ic_launcher.png` par le logo
  CESMAN (ou dans Android Studio : clic droit sur `res` › New › Image Asset).
- Couleur : `app/src/main/res/values/colors.xml`
- Nom affiché : `app/src/main/res/values/strings.xml`
