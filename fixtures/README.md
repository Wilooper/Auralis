# Auralis embedded lyrics fixtures

Four six-second generated quiet tones, with original Hindi/English test text; no copyrighted music. These are WAV files with ID3v2.4 chunks.

Import this entire folder, play each file and open Lyrics. Expected modes: 01 Plain lyrics; 02 Line sync; 03 Word sync (SYLT); 04 Word sync (enhanced LRC). Timed lyrics begin at 1 second; the second line begins at 3.5 seconds. In 04, the final word expires at 5.5 seconds. Try pausing and seeking backward.

Copy the folder into a music subfolder allowed by Android's folder picker. Add a nested folder and a non-audio file to check recursive scanning and filtering. Reimport to check deduplication. These are metadata/display fixtures, not a comprehensive codec test.
