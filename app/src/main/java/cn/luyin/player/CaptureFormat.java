package cn.luyin.player;

public enum CaptureFormat {
    M4A("M4A / AAC","m4a","audio/mp4",44100,128000),
    AAC("AAC","aac","audio/aac",44100,128000),
    AMR("AMR","amr","audio/amr",8000,12200),
    WAV("WAV / PCM","wav","audio/wav",44100,705600);
    public final String label,extension,mime;
    public final int sampleRate,bitRate;
    CaptureFormat(String label,String extension,String mime,int sampleRate,int bitRate){this.label=label;this.extension=extension;this.mime=mime;this.sampleRate=sampleRate;this.bitRate=bitRate;}
    public static CaptureFormat from(String id){try{return valueOf(id);}catch(Exception ignored){return M4A;}}
}
