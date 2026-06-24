namespace WfhDiary.Api.Auth;

public sealed class FirebaseAuthGate(bool verifyFirebaseTokens)
{
    public bool VerifyFirebaseTokens { get; } = verifyFirebaseTokens;
}
