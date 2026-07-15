$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding

Add-Type -AssemblyName System.Runtime.WindowsRuntime
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime] | Out-Null
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime] | Out-Null
[Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType=WindowsRuntime] | Out-Null

$script:asTaskMethods = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' }
$script:asStreamForReadMethod = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1
} | Select-Object -First 1

function Await-Result($operation, [Type]$resultType) {
    $method = $script:asTaskMethods | Where-Object {
        $_.IsGenericMethod -and $_.GetParameters().Count -eq 1
    } | Select-Object -First 1
    $task = $method.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait()
    return $task.Result
}

function Read-Thumbnail($thumbnail) {
    if ($null -eq $thumbnail) { return $null }
    $streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]
    $randomAccess = Await-Result ($thumbnail.OpenReadAsync()) $streamType
    if ($null -eq $randomAccess) { return $null }
    # Reflection lets .NET query the WinRT COM object for IInputStream; PowerShell's overload
    # binder sees only System.__ComObject and otherwise rejects the valid stream.
    $stream = $script:asStreamForReadMethod.Invoke($null, @($randomAccess))
    try {
        $memory = New-Object System.IO.MemoryStream
        try {
            $stream.CopyTo($memory)
            return [Convert]::ToBase64String($memory.ToArray())
        } finally { $memory.Dispose() }
    } finally { $stream.Dispose() }
}

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$manager = Await-Result ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) $managerType
$lastArtworkKey = ''

while ($true) {
    try {
        $session = $manager.GetCurrentSession()
        if ($null -eq $session) {
            [pscustomobject]@{ available = $false } | ConvertTo-Json -Compress
            $lastArtworkKey = ''
        } else {
            $properties = Await-Result ($session.TryGetMediaPropertiesAsync()) $propertiesType
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            $artworkKey = "$($session.SourceAppUserModelId)|$($properties.Title)|$($properties.Artist)|$($properties.AlbumTitle)"
            $artwork = $null
            if ($artworkKey -ne $lastArtworkKey) {
                try {
                    $artwork = Read-Thumbnail $properties.Thumbnail
                } catch {
                    # Metadata and timeline remain useful when a browser exposes an unreadable image.
                    $artwork = $null
                }
                $lastArtworkKey = if ($null -ne $artwork) { $artworkKey } else { '' }
            }
            [pscustomobject]@{
                available = $true
                source = $session.SourceAppUserModelId
                title = $properties.Title
                artist = $properties.Artist
                album = $properties.AlbumTitle
                positionMs = [long]$timeline.Position.TotalMilliseconds
                durationMs = [long]$timeline.EndTime.TotalMilliseconds
                playing = ($playback.PlaybackStatus.ToString() -eq 'Playing')
                artworkKey = $artworkKey
                thumbnailAvailable = ($null -ne $properties.Thumbnail)
                artwork = $artwork
            } | ConvertTo-Json -Compress
        }
    } catch {
        [pscustomobject]@{ available = $false; error = $_.Exception.Message } | ConvertTo-Json -Compress
    }
    Start-Sleep -Milliseconds 1000
}
