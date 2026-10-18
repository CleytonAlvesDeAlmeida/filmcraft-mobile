//! Modelo de linha do tempo simples (uma faixa de vídeo, clipes em sequência).
//!
//! Todo o tempo é guardado em `Tick` do `filmcraft-time` (inteiro exato) e alinhado a quadros
//! do `frame_rate` do projeto. A UI Android só vê milissegundos.

use filmcraft_time::{format_timecode_frames, FrameRate, Tick};
use serde::{Deserialize, Serialize};

/// Menor duração de um clipe, em quadros.
pub const MIN_CLIP_FRAMES: i64 = 3;

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Clip {
    pub id: u64,
    pub uri: String,
    pub name: String,
    /// Duração total do arquivo de origem.
    pub source_duration: Tick,
    /// Ponto de entrada (no tempo da origem).
    pub in_point: Tick,
    /// Ponto de saída (no tempo da origem), exclusivo.
    pub out_point: Tick,
    #[serde(default)]
    pub muted: bool,
}

impl Clip {
    pub fn duration(&self) -> Tick {
        self.out_point - self.in_point
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Project {
    pub frame_rate: FrameRate,
    pub clips: Vec<Clip>,
    pub next_id: u64,
}

impl Default for Project {
    fn default() -> Self {
        Project { frame_rate: FrameRate::FPS_30, clips: Vec::new(), next_id: 1 }
    }
}

/// Operações de edição. O JSON usa `{"op": "...", ...}`.
#[derive(Clone, Debug, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case")]
pub enum Op {
    AddClip { uri: String, name: String, duration_ms: i64 },
    Remove { id: u64 },
    Move { id: u64, to_index: usize },
    /// Ajusta entrada e/ou saída (em ms da origem). O que faltar fica como está.
    Trim { id: u64, in_ms: Option<i64>, out_ms: Option<i64> },
    /// Corta o clipe que está sob `at_ms` (posição na linha do tempo).
    Split { at_ms: i64 },
    Duplicate { id: u64 },
    ToggleMute { id: u64 },
    SetFrameRate { num: i64, den: i64 },
}

pub type EditResult = Result<(), String>;

fn ms(t: Tick) -> i64 {
    t.to_units_floor(1000)
}

fn from_ms(v: i64) -> Tick {
    Tick::from_units(v, 1000)
}

impl Project {
    fn min_clip(&self) -> Tick {
        Tick(self.frame_rate.sane().frame_duration().0 * MIN_CLIP_FRAMES)
    }

    fn index_of(&self, id: u64) -> Result<usize, String> {
        self.clips.iter().position(|c| c.id == id).ok_or_else(|| format!("Clipe {id} não encontrado"))
    }

    pub fn total_duration(&self) -> Tick {
        self.clips.iter().fold(Tick::ZERO, |acc, c| acc + c.duration())
    }

    pub fn apply(&mut self, op: Op) -> EditResult {
        match op {
            Op::AddClip { uri, name, duration_ms } => {
                if duration_ms <= 0 {
                    return Err("Vídeo sem duração válida".into());
                }
                let rate = self.frame_rate.sane();
                // Alinha a duração a quadros inteiros (para baixo), mas nunca abaixo de 1 quadro.
                let raw = from_ms(duration_ms);
                let dur = rate.snap(raw).max(rate.frame_duration());
                let id = self.next_id;
                self.next_id += 1;
                self.clips.push(Clip { id, uri, name, source_duration: dur, in_point: Tick::ZERO, out_point: dur, muted: false });
                Ok(())
            }
            Op::Remove { id } => {
                let i = self.index_of(id)?;
                self.clips.remove(i);
                Ok(())
            }
            Op::Move { id, to_index } => {
                let i = self.index_of(id)?;
                let clip = self.clips.remove(i);
                let to = to_index.min(self.clips.len());
                self.clips.insert(to, clip);
                Ok(())
            }
            Op::Trim { id, in_ms, out_ms } => {
                let i = self.index_of(id)?;
                let rate = self.frame_rate.sane();
                let min = self.min_clip();
                let clip = &mut self.clips[i];
                let min = min.min(clip.source_duration);
                let mut a = clip.in_point;
                let mut b = clip.out_point;
                if let Some(v) = in_ms {
                    a = rate.snap_nearest(from_ms(v));
                }
                if let Some(v) = out_ms {
                    b = rate.snap_nearest(from_ms(v));
                }
                a = a.max(Tick::ZERO);
                b = b.min(clip.source_duration);
                if in_ms.is_some() && b - a < min {
                    a = b - min;
                }
                if out_ms.is_some() && b - a < min {
                    b = a + min;
                }
                a = a.max(Tick::ZERO);
                b = b.min(clip.source_duration);
                if b - a < min {
                    return Err("Trecho curto demais".into());
                }
                clip.in_point = a;
                clip.out_point = b;
                Ok(())
            }
            Op::Split { at_ms } => {
                let rate = self.frame_rate.sane();
                let min = self.min_clip();
                let t = rate.snap_nearest(from_ms(at_ms));
                let mut start = Tick::ZERO;
                for i in 0..self.clips.len() {
                    let dur = self.clips[i].duration();
                    let end = start + dur;
                    if t >= start && t < end {
                        let offset = t - start;
                        if offset < min || dur - offset < min {
                            return Err("Posição muito perto da borda do clipe".into());
                        }
                        let mut right = self.clips[i].clone();
                        let cut = self.clips[i].in_point + offset;
                        self.clips[i].out_point = cut;
                        right.in_point = cut;
                        right.id = self.next_id;
                        self.next_id += 1;
                        self.clips.insert(i + 1, right);
                        return Ok(());
                    }
                    start = end;
                }
                Err("Nenhum clipe nessa posição".into())
            }
            Op::Duplicate { id } => {
                let i = self.index_of(id)?;
                let mut copy = self.clips[i].clone();
                copy.id = self.next_id;
                self.next_id += 1;
                self.clips.insert(i + 1, copy);
                Ok(())
            }
            Op::ToggleMute { id } => {
                let i = self.index_of(id)?;
                self.clips[i].muted = !self.clips[i].muted;
                Ok(())
            }
            Op::SetFrameRate { num, den } => {
                if num <= 0 || den <= 0 {
                    return Err("Taxa de quadros inválida".into());
                }
                self.frame_rate = FrameRate::new(num, den);
                // Realinha os pontos de corte à nova grade de quadros.
                let rate = self.frame_rate.sane();
                for c in &mut self.clips {
                    c.in_point = rate.snap(c.in_point);
                    c.out_point = rate.snap(c.out_point).max(c.in_point + rate.frame_duration());
                    c.out_point = c.out_point.min(c.source_duration.max(c.in_point + rate.frame_duration()));
                }
                Ok(())
            }
        }
    }

    pub fn timecode(&self, t: Tick) -> String {
        let rate = self.frame_rate.sane();
        format_timecode_frames(rate.frame_at(t), rate, false)
    }

    /// Visão pronta para a UI (tudo em milissegundos).
    pub fn view(&self) -> View {
        let mut start = Tick::ZERO;
        let mut clips = Vec::with_capacity(self.clips.len());
        for c in &self.clips {
            let dur = c.duration();
            clips.push(ClipView {
                id: c.id,
                uri: c.uri.clone(),
                name: c.name.clone(),
                source_ms: ms(c.source_duration),
                in_ms: ms(c.in_point),
                out_ms: ms(c.out_point),
                start_ms: ms(start),
                duration_ms: ms(dur),
                duration_timecode: self.timecode(dur),
                muted: c.muted,
            });
            start = start + dur;
        }
        let total = self.total_duration();
        View { frame_rate: self.frame_rate.sane().label(), total_ms: ms(total), total_timecode: self.timecode(total), clips }
    }
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClipView {
    pub id: u64,
    pub uri: String,
    pub name: String,
    pub source_ms: i64,
    pub in_ms: i64,
    pub out_ms: i64,
    pub start_ms: i64,
    pub duration_ms: i64,
    pub duration_timecode: String,
    pub muted: bool,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct View {
    pub frame_rate: String,
    pub total_ms: i64,
    pub total_timecode: String,
    pub clips: Vec<ClipView>,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn add(p: &mut Project, name: &str, ms: i64) {
        p.apply(Op::AddClip { uri: format!("content://{name}"), name: name.into(), duration_ms: ms }).unwrap();
    }

    #[test]
    fn add_aligns_to_frames() {
        let mut p = Project::default();
        add(&mut p, "a", 10_000);
        assert_eq!(p.clips[0].id, 1);
        assert_eq!(p.view().clips[0].duration_ms, 10_000);
        assert_eq!(p.view().total_timecode, "00:00:10:00");
    }

    #[test]
    fn add_rejects_empty() {
        let mut p = Project::default();
        assert!(p.apply(Op::AddClip { uri: "x".into(), name: "x".into(), duration_ms: 0 }).is_err());
    }

    #[test]
    fn trim_clamps_and_snaps() {
        let mut p = Project::default();
        add(&mut p, "a", 10_000);
        p.apply(Op::Trim { id: 1, in_ms: Some(2_000), out_ms: Some(5_000) }).unwrap();
        let v = p.view();
        assert_eq!((v.clips[0].in_ms, v.clips[0].out_ms, v.clips[0].duration_ms), (2_000, 5_000, 3_000));
        // saída além da origem é limitada
        p.apply(Op::Trim { id: 1, in_ms: None, out_ms: Some(99_000) }).unwrap();
        assert_eq!(p.view().clips[0].out_ms, 10_000);
        // entrada colada na saída respeita o mínimo de quadros
        p.apply(Op::Trim { id: 1, in_ms: Some(10_000), out_ms: None }).unwrap();
        let c = &p.clips[0];
        assert_eq!(c.out_point - c.in_point, p.min_clip());
    }

    #[test]
    fn split_and_total_preserved() {
        let mut p = Project::default();
        add(&mut p, "a", 10_000);
        p.apply(Op::Split { at_ms: 4_000 }).unwrap();
        let v = p.view();
        assert_eq!(v.clips.len(), 2);
        assert_eq!((v.clips[0].duration_ms, v.clips[1].duration_ms), (4_000, 6_000));
        assert_eq!(v.clips[1].in_ms, 4_000);
        assert_eq!(v.clips[1].start_ms, 4_000);
        assert_eq!(v.total_ms, 10_000);
        assert_ne!(v.clips[0].id, v.clips[1].id);
    }

    #[test]
    fn split_errors() {
        let mut p = Project::default();
        add(&mut p, "a", 10_000);
        assert!(p.apply(Op::Split { at_ms: 0 }).is_err());
        assert!(p.apply(Op::Split { at_ms: 10_000 }).is_err());
        assert!(p.apply(Op::Split { at_ms: 50_000 }).is_err());
    }

    #[test]
    fn move_remove_duplicate_mute() {
        let mut p = Project::default();
        add(&mut p, "a", 1_000);
        add(&mut p, "b", 2_000);
        add(&mut p, "c", 3_000);
        p.apply(Op::Move { id: 3, to_index: 0 }).unwrap();
        assert_eq!(p.clips.iter().map(|c| c.id).collect::<Vec<_>>(), vec![3, 1, 2]);
        p.apply(Op::Move { id: 3, to_index: 99 }).unwrap();
        assert_eq!(p.clips.iter().map(|c| c.id).collect::<Vec<_>>(), vec![1, 2, 3]);
        p.apply(Op::Duplicate { id: 2 }).unwrap();
        assert_eq!(p.clips.len(), 4);
        assert_eq!(p.clips[2].name, "b");
        p.apply(Op::ToggleMute { id: 2 }).unwrap();
        assert!(p.clips[1].muted);
        p.apply(Op::Remove { id: 2 }).unwrap();
        assert_eq!(p.clips.len(), 3);
        assert!(p.apply(Op::Remove { id: 999 }).is_err());
    }

    #[test]
    fn exact_ntsc_timecode() {
        let mut p = Project::default();
        p.apply(Op::SetFrameRate { num: 30000, den: 1001 }).unwrap();
        add(&mut p, "a", 60_000);
        // 60 s em 29,97 fps: 1799 quadros inteiros dentro de 60 s (floor), sem deriva de ticks
        let v = p.view();
        assert_eq!(v.frame_rate, "29.97");
        assert!(v.total_ms <= 60_000 && v.total_ms >= 59_900, "{}", v.total_ms);
    }

    #[test]
    fn bad_frame_rate() {
        let mut p = Project::default();
        assert!(p.apply(Op::SetFrameRate { num: 0, den: 1 }).is_err());
    }

    #[test]
    fn project_json_roundtrip() {
        let mut p = Project::default();
        add(&mut p, "a", 5_000);
        let s = serde_json::to_string(&p).unwrap();
        let q: Project = serde_json::from_str(&s).unwrap();
        assert_eq!(p, q);
    }
}
