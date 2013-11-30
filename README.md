Skvalex CallRecorder Headset Fix for Nexus 4
===================================

Why this patch?
skvalex, a well know mastermind for creating 2way call recorder app and patches
that supports his application.
He wrote a patch for Nexus 4 and works great untill you plug-in the headset.
with headset connected, the other party's voice distorts in recording.

This patch switches headset to headphone while incoming or ringing, and switch
back to headset after 700ms post call connected till then call recording is 
started (ofcourse with you have 0.3sec to start in callrecorder app setting)

THIS IS A QUICKFIX UNTILL SKVALEX FIX IN APP OR PATCH