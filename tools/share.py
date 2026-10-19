"""A copy of a Short small enough to share where files are capped (Discord without Nitro: 10 MB).

    py tools/share.py build/film/domain-short.mp4                 # writes build/film/domain-short-share.mp4
    py tools/share.py build/film/domain-short.mp4 --mb 25 --out x.mp4

The picture is brought down to 720x1280 (a phone shows no more) and encoded in two passes at just the bitrate that
fills the size asked for, a little under it to be safe; the sound is kept, at 128 kb/s.

Needs: ffmpeg and ffprobe on the PATH.
"""
import argparse
import os
import subprocess
import sys
import tempfile

AUDIO_KBPS = 128


def duration(path):
    out = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path],
                         capture_output=True, text=True, check=True).stdout
    return float(out.strip())


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('video')
    parser.add_argument('--mb', type=float, default=10.0, help='the cap, in MB (default 10: Discord without Nitro)')
    parser.add_argument('--out', help='where to write it (default: beside it, "-share" added)')
    args = parser.parse_args()
    out = args.out or os.path.splitext(args.video)[0] + '-share.mp4'
    seconds = duration(args.video)
    # 7% under the cap, for the container and the encoder's overshoot.
    total_kbps = args.mb * 8 * 1000 * 0.93 / seconds
    video_kbps = int(total_kbps - AUDIO_KBPS)
    if video_kbps < 300:
        sys.exit(f'{seconds:.1f} s is too long to fit {args.mb} MB at any watchable quality')
    scale = 'scale=720:1280:flags=lanczos'
    with tempfile.TemporaryDirectory() as tmp:
        log = os.path.join(tmp, 'pass')
        common = ['ffmpeg', '-v', 'error', '-y', '-i', args.video, '-vf', scale, '-c:v', 'libx264', '-preset', 'slow',
                  '-b:v', f'{video_kbps}k', '-passlogfile', log]
        subprocess.run(common + ['-pass', '1', '-an', '-f', 'mp4', os.devnull], check=True)
        subprocess.run(common + ['-pass', '2', '-c:a', 'aac', '-b:a', f'{AUDIO_KBPS}k', '-movflags', '+faststart', out], check=True)
    size = os.path.getsize(out) / 1_000_000
    print(f'wrote {out}: {size:.2f} MB ({seconds:.1f} s at {video_kbps} kb/s)')
    if size > args.mb:
        sys.exit(f'it came out over {args.mb} MB: run it again with a smaller --mb')


if __name__ == '__main__':
    main()
